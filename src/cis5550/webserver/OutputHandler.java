package cis5550.webserver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

import cis5550.tools.Logger;

public class OutputHandler {
    private static final Logger logger = Logger.getLogger(Server.class);

    public void error400(OutputStream output) {
        try {
            String response = "HTTP/1.1 400 Bad Request\r\n" +
                    "Content-Length: 15\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "400 Bad Request\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            logger.info("output writing error" + e);
        }
    }

    public void error403(OutputStream output) {
        try {
            String response = "HTTP/1.1 403 Forbidden\r\n" + "Content-Length: 13\r\n\r\n" + "403 Forbidden\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            logger.info("output writing error" + e);
        }
    }

    public void error404(OutputStream output) {
        try {
            String response = "HTTP/1.1 404 Not Found\r\n" + "Content-Length: 13\r\n\r\n" + "404 Not Found\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            logger.info("output writing error" + e);
        }
    }

    public void error405(OutputStream output) {
        try {
            String response = "HTTP/1.1 405 Not Allowed\r\n" + "Content-Length: 15\r\n\r\n" + "405 Not Allowed\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            logger.info("output writing error" + e);
        }
    }

    public void error500(OutputStream output) {
        try {
            String response = "HTTP/1.1 500 Internal Server Error\r\n" + "Content-Length: 25\r\n\r\n"
                    + "500 Internal Server Error\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            logger.info("output writing error" + e);
        }
    }

    public void error501(OutputStream output) {
        try {
            String response = "HTTP/1.1 501 Not Implemented\r\n" + "Content-Length: 19\r\n\r\n"
                    + "501 Not Implemented\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            logger.info("output writing error" + e);
        }
    }

    public void error505(OutputStream output) {
        try {
            String response = "HTTP/1.1 505 HTTP Version Not Supported\r\n" + "Content-Length: 30\r\n\r\n"
                    + "505 HTTP Version Not Supported\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            logger.info("output writing error" + e);
        }

    }

    public void sendResponse(String filePath, Socket clientSocket, OutputStream outputStream, boolean isHead) {
        try {
            File file = new File(filePath);
            int lastDotIndex = filePath.lastIndexOf('.');
            String extension = "";

            if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
                extension = filePath.substring(lastDotIndex + 1);
            }

            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 200 OK\r\n");

            if (extension.equals("jpg") || extension.equals("jpeg")) {
                headers.append("Content-Type: ").append("image/jpeg").append("\r\n");
            } else if (extension.equals("txt")) {
                headers.append("Content-Type: ").append("text/plain").append("\r\n");
            } else if (extension.equals("html")) {
                headers.append("Content-Type: ").append("text/html").append("\r\n");
            } else {
                headers.append("Content-Type: ").append("application/octet-stream").append("\r\n");
            }

            headers.append("Server: OCEserver\r\n");

            FileInputStream input = new FileInputStream(file);
            long fileLength = file.length();
            headers.append("Content-Length: ").append(fileLength).append("\r\n\r\n");

            outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1 && !isHead) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();
            input.close();
        } catch (Exception e) {
            logger.error("Error generating output: " + e.getMessage());
        }
    }

    public static void outputNonWrittenDynamic(Object routeRes, Response res,
            OutputStream outputStream, String possibleSessionId, boolean isHttps) {
        try {
            // Handle null routeRes
            byte[] body = new byte[0];
            if (routeRes != null) {
                body = routeRes.toString().getBytes(StandardCharsets.UTF_8);
            } else {
                body = res.getBodyAsBytes();
                if (body == null) {
                    body = new byte[0];
                }
            }

            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 ").append(res.getStatusCode()).append(" ").append(res.getReasonPhrase())
                    .append("\r\n");

            for (Map.Entry<String, String> header : res.getHeaders().entrySet()) {
                headers.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }

            if (possibleSessionId != null) {
                StringBuilder cookieBuilder = new StringBuilder();
                cookieBuilder.append("Set-Cookie: SessionID=")
                        .append(possibleSessionId)
                        .append("; HttpOnly; SameSite=Lax");

                if (isHttps) {
                    cookieBuilder.append("; Secure");
                }

                headers.append(cookieBuilder.toString() + "\r\n");
            }

            headers.append("Server: OCEserver\r\n");
            headers.append("Content-Length: ").append(body.length).append("\r\n");
            headers.append("Content-Type: ").append(res.getContentType()).append("\r\n\r\n");
            outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));

            if (body.length > 0) {
                outputStream.write(body);
                outputStream.flush();
            }
        } catch (Exception e) {
            logger.error("Exception occurred while writing output: " + e.getMessage(), e);
        }
    }
}
