package cis5550.webserver;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

import cis5550.tools.Logger;

public class ClientInputHandler implements Runnable {
    private Socket clientSocket;
    private String directory;
    private static final Logger logger = Logger.getLogger(Server.class);
    private OutputHandler outputHandler = new OutputHandler();
    private Map<String, Map<String, Map<String, Route>>> hostsMap;
    private Map<String, Session> sessionsMap;
    private boolean isHttps;

    public ClientInputHandler(Socket clientSocket, String directory,
            Map<String, Map<String, Map<String, Route>>> hostsMap, Map<String, Session> sessionsMap, boolean isHttps) {
        this.clientSocket = clientSocket;
        this.directory = directory;
        this.hostsMap = hostsMap;
        this.sessionsMap = sessionsMap;
        this.isHttps = isHttps;
    }

    @Override
    public void run() {
        try (InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();) {

            boolean connectionClose = false;
            // Continuous looping until error is thrown or break is called or socket closed
            while (!clientSocket.isClosed() && !connectionClose) {
                // Parse the request headers
                List<String> headerLines = parseInput(input);
                Map<String, String> headersMap = new HashMap<>();
                String[] requestLine = null;
                // Client has sent EOF with no input (connection done or socket closed)
                if (headerLines == null || headerLines.isEmpty()) {
                    logger.info("No headers received. Closing connection.");
                    break;
                }
                String requestLineStr = headerLines.get(0).trim();
                requestLine = requestLineStr.split("\\s+");

                for (int i = 1; i < headerLines.size(); i++) {
                    String line = headerLines.get(i);
                    int colonIndex = line.indexOf(':');

                    if (colonIndex != -1) {
                        String key = line.substring(0, colonIndex).trim().toLowerCase();
                        String value = line.substring(colonIndex + 1).trim();
                        if (key.equals("connection") && value.equals("close")) {
                            connectionClose = true;
                        }
                        headersMap.put(key, value);
                    }
                }

                String filePath = null;

                if (requestLine == null || requestLine.length != 3 || (headerLines.size() != headersMap.size() + 1) ||
                        headersMap.get("host") == null) {
                    outputHandler.error400(output);
                    break;
                }

                String HTTPMethod = requestLine[0];
                String url = requestLine[1];
                String protocol = requestLine[2];

                if (!HTTPMethod.equals("PUT") && !HTTPMethod.equals("POST") &&
                        !HTTPMethod.equals("HEAD") && !HTTPMethod.equals("GET")) {
                    outputHandler.error501(output);
                    break;
                } else if (!protocol.equals("HTTP/1.1")) {
                    outputHandler.error505(output);
                    break;
                }

                // Read the request body
                byte[] requestBody = null;
                if (headersMap.get("content-length") != null) {
                    try {
                        int contentLength = Integer.parseInt(headersMap.get("content-length"));

                        requestBody = new byte[contentLength];

                        int bytesRead = 0;
                        while (bytesRead < contentLength) {
                            int result = input.read(requestBody, bytesRead, contentLength - bytesRead);
                            if (result == -1) {
                                // Content length was incorrect so ended early
                                outputHandler.error400(output);
                                throw new Exception("Error when reading request body - possible content length issue");
                            }
                            bytesRead += result;
                        }

                    } catch (Exception e) {
                        logger.info("Error handling client: " + e.getMessage(), e);
                        throw new Exception();
                    }
                }

                // dynamic file check
                if (findDynamicContent(HTTPMethod, url, protocol, headersMap, requestBody, output)) {
                } else if (directory != null) {
                    // This is static file check
                    filePath = directory + url;
                    logger.info("static filepath is" + filePath);
                    File file = new File(filePath);
                    if (filePath.contains("..") || (file.exists() && !file.canRead())) {
                        logger.info("Static file not accessible");
                        outputHandler.error403(output);
                        break;
                    }
                    if (!file.exists()) {
                        logger.info("Could not find static file");
                        outputHandler.error404(output);
                        break;
                    }
                    outputHandler.sendResponse(filePath, clientSocket, output, HTTPMethod.equals("HEAD"));
                } else {
                    // Neither passes, file not found
                    logger.info("unfound Url was" + url);
                    logger.info("Route not found (404 Error)");
                    outputHandler.error404(output);
                }
            }
            output.close();
            clientSocket.close();
        } catch (Exception e) {
            logger.info("Error handling client: " + e.getMessage(), e);
        }
    }

    private List<String> parseInput(InputStream input) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int prev = -1, prev2 = -1, prev3 = -1, curr;

            while ((curr = input.read()) != -1) {
                buffer.write(curr);

                if (prev3 == 13 && prev2 == 10 && prev == 13 && curr == 10) {
                    break;
                }

                prev3 = prev2;
                prev2 = prev;
                prev = curr;
            }

            byte[] headerBytes = buffer.toByteArray();

            ByteArrayInputStream headerStream = new ByteArrayInputStream(headerBytes);
            InputStreamReader headerReader = new InputStreamReader(headerStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(headerReader);

            List<String> requestLines = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                requestLines.add(line);
            }
            return requestLines;
        } catch (Exception e) {
            logger.info("Error handling input: " + e.getMessage(), e);
            return null;
        }
    }

    // Returns true if content is dynamic and has been processed properly
    public boolean findDynamicContent(String method, String url, String protocol, Map<String, String> headersMap,
            byte[] requestBody, OutputStream outputStream) throws Exception {

        Map<String, String> pathParams = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();

        String hostHeader = headersMap.get("host");
        String requestHost = "";

        if (hostHeader != null) {
            requestHost = hostHeader.split(":")[0];
        }

        Map<String, Map<String, Route>> routesMap = hostsMap.getOrDefault(requestHost, hostsMap.get(""));
        Map<String, Route> methodMap = routesMap.get(method);

        if (methodMap != null) {
            // check if URL is directly in map (is alr base url)
            Route route = methodMap.get(url);

            // URL matching with extraction of path params
            if (route == null) {
                String paths[] = url.split("/");
                for (Map.Entry<String, Route> entry : methodMap.entrySet()) {
                    String registeredPath = entry.getKey();
                    String[] routeSegments = registeredPath.split("/");
                    if (routeSegments.length == paths.length) {
                        boolean match = true;
                        for (int i = 0; i < routeSegments.length; i++) {
                            // remove query params from path param if present as we don't need right now
                            if (paths[i].indexOf("?") != -1) {
                                paths[i] = paths[i].substring(0, paths[i].indexOf("?"));
                            }

                            // If we match with wildcard, add to pathParams
                            if (routeSegments[i].startsWith(":")) {
                                String paramName = routeSegments[i].substring(1);
                                // URL Decode paths[i] right before putting it in
                                String decodedPath = URLDecoder.decode(paths[i], StandardCharsets.UTF_8.name());
                                pathParams.put(paramName, decodedPath);
                            } else if (!routeSegments[i].equals(paths[i])) {
                                // If not wildcard and it isnt an exact match, this is not the right route
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            // resassign route to the matched registeredPath
                            route = methodMap.get(registeredPath);
                        }
                    }
                }
            }

            // Extracting query params from url and checking for match
            int querIndex = url.indexOf("?");
            if (querIndex != -1 && querIndex < url.length() - 1) {
                String queryParamsString = url.substring(querIndex + 1);
                loadQueryParams(queryParams, queryParamsString);
            }

            // Query params loading from body
            String contentType = headersMap.get("content-type");
            if (contentType != null && contentType.equalsIgnoreCase("application/x-www-form-urlencoded") &&
                    requestBody != null && requestBody.length != 0) {
                String body = new String(requestBody, StandardCharsets.UTF_8);
                loadQueryParams(queryParams, body);
            }

            // Extracting cookies
            Session currSession = null;
            if (headersMap.get("cookie") != null) {
                String[] cookies = headersMap.get("cookie").split(";");
                for (String cookie : cookies) {
                    cookie = cookie.trim();
                    if (cookie.startsWith("SessionID=")) {
                        String sessionId = cookie.substring("SessionID=".length());
                        if (sessionsMap.get(sessionId) != null) {
                            currSession = sessionsMap.get(sessionId);
                            // currSession has already expired
                            if (!currSession.setLastAccessedTime(System.currentTimeMillis())
                                    || !currSession.isValid()) {
                                currSession = null;
                            }

                        }
                    }
                }
            }

            if (url.contains("batch") || url.contains("foldbykey")) {
                logger.info("url for batching was" + url);
                logger.info("Route is" + route);
            }

            // probably excpetion if route is null?
            if (route != null) {
                Response res = null;
                try {
                    InetSocketAddress socketAddress = (InetSocketAddress) clientSocket.getRemoteSocketAddress();
                    Request req = new RequestImpl(method, url, protocol, headersMap, queryParams,
                            pathParams, socketAddress, requestBody, Server.getInstance(), sessionsMap);
                    req.setSession(currSession);

                    res = new ResponseImpl(outputStream);
                    String preSessionId = req.getSessionId();
                    Object routeRes = route.handle(req, res);
                    String postSessionId = null;
                    if (preSessionId == null) {
                        postSessionId = req.getSessionId();
                    }

                    // Handle not written (written is handled in responseimpl.java)
                    if (!res.getIsWritten()) {
                        OutputHandler.outputNonWrittenDynamic(routeRes, res, outputStream, postSessionId,
                                isHttps);
                    } else {
                        // Write finished, close connection (after connection: close was sent
                        // previously)
                        outputStream.close();
                    }

                    return true;
                } catch (Exception e) {
                    logger.info("Exception occurred while handling route: " + e.getMessage(), e);
                    if (res != null && !res.getIsWritten()) {
                        outputHandler.error500(outputStream);
                        throw new Exception();
                    } else {
                        try {
                            clientSocket.close();
                        } catch (Exception ex) {
                            logger.info("Exception occurred while handling socket closing: " + ex.getMessage(), ex);
                        }
                    }
                }
            }
        }
        // No route was succesfully found, false is returned
        return false;
    }

    private void loadQueryParams(Map<String, String> queryParams, String queryParamsString) {
        String[] queryPairs = queryParamsString.split("&");

        for (String pair : queryPairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String decodedKey = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String decodedValue = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    queryParams.put(decodedKey, decodedValue);
                } catch (Exception e) {
                    logger.info("Exception occurred while handling encoding: " + e.getMessage(), e);
                }
            }
        }
    }
}
