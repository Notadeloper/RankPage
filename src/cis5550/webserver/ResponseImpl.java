package cis5550.webserver;

import java.util.*;

import cis5550.tools.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ResponseImpl implements Response {
    private int statusCode = 200;
    private String reasonPhrase = "OK";
    private byte[] body;
    private Map<String, String> headers = new HashMap<String, String>();
    private String contentType = "text/html";
    private boolean isWritten = false;
    private boolean headersWritten = false;
    private OutputStream outputStream;
    private static final Logger logger = Logger.getLogger(Server.class);
    // The methods below are used to set the body, either as a string or as an array
    // of bytes
    // (if the application wants to return something binary - say, an image file).
    // Your server
    // should send back the following in the body of the response:
    // * If write() has been called, ignore both the return value of Route.handle()
    // and
    // any calls to body() and bodyAsBytes().
    // * If write() has not been called and Route.handle() returns something other
    // than null,
    // call the toString() method on that object and send the result.
    // * If write() has not been called and Route.handle returns null(), use the
    // value from
    // the most recent body() or bodyAsBytes() call.
    // * If none of write(), body(), and bodyAsBytes() have been called and
    // Route.handle returns null,
    // do not send a body in the response.

    public ResponseImpl(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void body(String body) {
        if (isWritten)
            return;
        this.body = body.getBytes();
    }

    @Override
    public void bodyAsBytes(byte bodyArg[]) {
        if (isWritten)
            return;
        this.body = bodyArg;
    }

    // This method adds a header. For instance, header("Cookie", "abc=def") should
    // cause your
    // server to eventually send a header line "Cookie: abc=def". This method can be
    // called
    // multiple times with the same header name; the result should be multiple
    // header lines.
    // If write() has been called, this method should have no effect.
    @Override
    public void header(String name, String value) {
        if (isWritten)
            return;
        headers.put(name, value);
    }

    // This method sets the Content-Type: header. If type() isn't called, the
    // default should
    // be 'text/html'.
    @Override
    public void type(String contentType) {
        this.contentType = contentType;
    }

    // This method sets the status code and the reason phrase. If it is called more
    // than once,
    // use the latest values. If it is never called, use 200 and "OK". If write()
    // has been
    // called, status() should have no effect.
    @Override
    public void status(int statusCode, String reasonPhrase) {
        if (isWritten)
            return;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    // This method can be used to send data directly to the connection, without
    // buffering it
    // in an object in memory. The first time write() is called, it should 'commit'
    // the
    // response by sending out the status code/reason phrase and any headers that
    // have been
    // set so far. Your server should 1) add a 'Connection: close' header, and it
    // should
    // 2) NOT add a Content-Length header in this case. Then, and in any subsequent
    // calls,
    // it should simply write the provided bytes directly to the connection.
    @Override
    public void write(byte[] b) throws Exception {
        if (!isWritten) {
            isWritten = true;
        }

        if (!headersWritten) {
            StringBuilder headers = new StringBuilder();

            headers.append("HTTP/1.1 ").append(getStatusCode()).append(" ").append(getReasonPhrase())
                    .append("\r\n");

            for (Map.Entry<String, String> header : getHeaders().entrySet()) {
                headers.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }

            if (getHeaders().get("content-type") == null) {
                headers.append("Content-Type: ").append(contentType).append("\r\n");
            }

            headers.append("Connection: close\r\n\r\n");

            outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            headersWritten = true;
        }

        if (b != null && b.length > 0) {
            outputStream.write(b);
            outputStream.flush();
        }
    }

    @Override
    public byte[] getBodyAsBytes() {
        return body;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String getReasonPhrase() {
        return reasonPhrase;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean getIsWritten() {
        return isWritten;
    }

    // EXTRA CREDIT ONLY - please see the handout for details. If you are not doing
    // the extra
    // credit, please implement this with a dummy method that does nothing.
    // Pretty bad implementation NGL
    @Override
    public void redirect(String url, int responseCode) {
        try {
            if (responseCode == 301 || responseCode == 302 || responseCode == 303 ||
                    responseCode == 307 || responseCode == 308) {

                status(responseCode, getReasonPhraseForRedirect(responseCode));
                header("Location", url);

                StringBuilder headers = new StringBuilder();

                headers.append("HTTP/1.1 ").append(getStatusCode()).append(" ").append(getReasonPhrase())
                        .append("\r\n");

                for (Map.Entry<String, String> header : getHeaders().entrySet()) {
                    headers.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
                }
                headers.append("\r\n");
                outputStream.write(headers.toString().getBytes(StandardCharsets.UTF_8));
                isWritten = true;

            } else {
                throw new IllegalArgumentException("Invalid redirect response code: " + responseCode);
            }
        } catch (Exception e) {

        }
    }

    // EXTRA CREDIT ONLY - please see the handout for details. If you are not doing
    // the extra
    // credit, please implement this with a dummy method that does nothing.
    @Override
    public void halt(int statusCode, String reasonPhrase) {

    }

    private String getReasonPhraseForRedirect(int statusCode) {
        switch (statusCode) {
            case 301:
                return "Moved Permanently";
            case 302:
                return "Found";
            case 303:
                return "See Other";
            case 307:
                return "Temporary Redirect";
            case 308:
                return "Permanent Redirect";
            default:
                return "Redirect";
        }
    }
}