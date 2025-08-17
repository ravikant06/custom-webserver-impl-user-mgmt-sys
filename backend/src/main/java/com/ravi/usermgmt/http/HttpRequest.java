package com.ravi.usermgmt.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP Request parser and container
 */
public class HttpRequest {
    private String method;
    private String path;
    private String queryString;
    private String httpVersion;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String body;
    private byte[] rawBody;

    public HttpRequest() {
        this.headers = new HashMap<>();
        this.queryParams = new HashMap<>();
    }

    /**
     * Parse HTTP request from InputStream
     */
    public static HttpRequest parse(InputStream inputStream) throws IOException {
        HttpRequest request = new HttpRequest();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        // Parse request line (GET /path HTTP/1.1)
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new IOException("Invalid HTTP request: empty request line");
        }

        request.parseRequestLine(requestLine);

        // Parse headers
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.trim().isEmpty()) {
            request.parseHeader(headerLine);
        }

        // Parse body if present
        String contentLengthStr = request.getHeader("Content-Length");
        if (contentLengthStr != null) {
            try {
                int contentLength = Integer.parseInt(contentLengthStr);
                if (contentLength > 0) {
                    char[] bodyChars = new char[contentLength];
                    int totalRead = 0;
                    while (totalRead < contentLength) {
                        int read = reader.read(bodyChars, totalRead, contentLength - totalRead);
                        if (read == -1) break;
                        totalRead += read;
                    }
                    request.body = new String(bodyChars, 0, totalRead);
                    request.rawBody = request.body.getBytes(StandardCharsets.UTF_8);
                }
            } catch (NumberFormatException e) {
                // Invalid content length, ignore body
            }
        }

        return request;
    }

    private void parseRequestLine(String requestLine) {
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid request line: " + requestLine);
        }

        this.method = parts[0].toUpperCase();
        this.httpVersion = parts[2];

        // Parse path and query string
        String fullPath = parts[1];
        int queryIndex = fullPath.indexOf('?');
        if (queryIndex != -1) {
            this.path = fullPath.substring(0, queryIndex);
            this.queryString = fullPath.substring(queryIndex + 1);
            parseQueryString(this.queryString);
        } else {
            this.path = fullPath;
        }
    }

    private void parseHeader(String headerLine) {
        int colonIndex = headerLine.indexOf(':');
        if (colonIndex != -1) {
            String name = headerLine.substring(0, colonIndex).trim();
            String value = headerLine.substring(colonIndex + 1).trim();
            this.headers.put(name.toLowerCase(), value);
        }
    }

    private void parseQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return;
        }

        String[] params = queryString.split("&");
        for (String param : params) {
            int equalIndex = param.indexOf('=');
            if (equalIndex != -1) {
                try {
                    String key = URLDecoder.decode(param.substring(0, equalIndex), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(param.substring(equalIndex + 1), StandardCharsets.UTF_8);
                    this.queryParams.put(key, value);
                } catch (Exception e) {
                    // Skip invalid parameter
                }
            }
        }
    }

    // Getters
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getQueryString() { return queryString; }
    public String getHttpVersion() { return httpVersion; }
    public String getBody() { return body; }
    public byte[] getRawBody() { return rawBody; }

    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }

    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }

    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    public Map<String, String> getQueryParams() {
        return new HashMap<>(queryParams);
    }

    // Utility methods
    public boolean isGet() { return "GET".equals(method); }
    public boolean isPost() { return "POST".equals(method); }
    public boolean isPut() { return "PUT".equals(method); }
    public boolean isDelete() { return "DELETE".equals(method); }
    public boolean isPatch() { return "PATCH".equals(method); }

    public boolean hasBody() {
        return body != null && !body.isEmpty();
    }

    public boolean isJson() {
        String contentType = getHeader("content-type");
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    public boolean isFormData() {
        String contentType = getHeader("content-type");
        return contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded");
    }

    // Parse JSON body (requires Jackson)
    public <T> T getJsonBody(Class<T> clazz) throws IOException {
        if (!hasBody() || !isJson()) {
            return null;
        }
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return mapper.readValue(body, clazz);
    }

    // Parse form data
    public Map<String, String> getFormData() {
        Map<String, String> formData = new HashMap<>();
        if (!hasBody() || !isFormData()) {
            return formData;
        }

        String[] params = body.split("&");
        for (String param : params) {
            int equalIndex = param.indexOf('=');
            if (equalIndex != -1) {
                try {
                    String key = URLDecoder.decode(param.substring(0, equalIndex), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(param.substring(equalIndex + 1), StandardCharsets.UTF_8);
                    formData.put(key, value);
                } catch (Exception e) {
                    // Skip invalid parameter
                }
            }
        }
        return formData;
    }

    /**
     * Parse request body as JSON into the specified class
     */
    public <T> T bodyAs(Class<T> clazz) {
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("Request body is empty");
        }
        
        try {
            // Use the same ObjectMapper from HttpResponse for consistency
            return com.ravi.usermgmt.http.HttpResponse.getObjectMapper().readValue(body, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse request body as " + clazz.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", method, path, httpVersion);
    }
}