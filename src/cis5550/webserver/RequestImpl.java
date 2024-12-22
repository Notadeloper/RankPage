
package cis5550.webserver;

import java.util.*;

import cis5550.tools.Logger;

import java.net.*;
import java.nio.charset.*;
import java.security.SecureRandom;

// Provided as part of the framework code

class RequestImpl implements Request {
    String method;
    String url;
    String protocol;
    InetSocketAddress remoteAddr;
    Map<String, String> headers;
    Map<String, String> queryParams;
    Map<String, String> params;
    byte bodyRaw[];
    Server server;
    Session session = null;
    private Map<String, Session> sessionsMap;
    private static final Logger logger = Logger.getLogger(Server.class);

    private static final String CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final int SESSION_ID_LENGTH = 20;
    private static final SecureRandom random = new SecureRandom();

    RequestImpl(String methodArg, String urlArg, String protocolArg, Map<String, String> headersArg,
            Map<String, String> queryParamsArg, Map<String, String> paramsArg, InetSocketAddress remoteAddrArg,
            byte bodyRawArg[], Server serverArg, Map<String, Session> sessionsMap) {
        method = methodArg;
        url = urlArg;
        remoteAddr = remoteAddrArg;
        protocol = protocolArg;
        headers = headersArg;
        queryParams = queryParamsArg;
        params = paramsArg;
        bodyRaw = bodyRawArg;
        server = serverArg;
        this.sessionsMap = sessionsMap;
    }

    public String requestMethod() {
        return method;
    }

    public void setParams(Map<String, String> paramsArg) {
        params = paramsArg;
    }

    public int port() {
        return remoteAddr.getPort();
    }

    public String url() {
        return url;
    }

    public String protocol() {
        return protocol;
    }

    public String contentType() {
        return headers.get("content-type");
    }

    public String ip() {
        return remoteAddr.getAddress().getHostAddress();
    }

    public String body() {
        return new String(bodyRaw, StandardCharsets.UTF_8);
    }

    public byte[] bodyAsBytes() {
        return bodyRaw;
    }

    public int contentLength() {
        return bodyRaw.length;
    }

    public String headers(String name) {
        return headers.get(name.toLowerCase());
    }

    public Set<String> headers() {
        return headers.keySet();
    }

    public String queryParams(String param) {
        return queryParams.get(param);
    }

    public Set<String> queryParams() {
        return queryParams.keySet();
    }

    public String params(String param) {
        return params.get(param);
    }

    public Map<String, String> params() {
        return params;
    }

    @Override
    public String getSessionId() {
        for (Map.Entry<String, Session> entry : sessionsMap.entrySet()) {
            if (session != null && entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public Session session() {
        logger.info("sup");
        if (this.session == null) {
            logger.info("sup2");
            StringBuilder sessionId = new StringBuilder(SESSION_ID_LENGTH);

            for (int i = 0; i < SESSION_ID_LENGTH; i++) {
                int randomIndex = random.nextInt(CHAR_SET.length());
                sessionId.append(CHAR_SET.charAt(randomIndex));
            }
            Session currSession = new SessionImpl(sessionId.toString());
            logger.info("putting" + sessionId.toString());
            sessionsMap.put(sessionId.toString(), currSession);
            this.session = currSession;
        }

        return this.session;
    }

}
