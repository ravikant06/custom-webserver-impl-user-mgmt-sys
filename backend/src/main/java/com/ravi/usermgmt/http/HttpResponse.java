package com.ravi.usermgmt.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP Response builder and writer
 */
public class HttpResponse {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // Configure Java Time Module with explicit settings
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        objectMapper.registerModule(javaTimeModule);
        
        // Force dates to be written as ISO-8601 strings
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        
        // Configure to not fail on unknown properties
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        
        // Configure for better handling of time zones and prevent offset errors
        objectMapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, false);
        
        // Additional safety configurations
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private int statusCode = 200;
    private String statusMessage = "OK";
    private final Map<String, String> headers = new HashMap<>();
    private String body = "";

    public HttpResponse() {
        // Set common headers
        setHeader("Content-Type", "application/json");
        setHeader("Server", "UserMgmt/1.0");
        // Use Instant with proper timezone for RFC-1123 format
        setHeader("Date", Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
    }

    // Static factory methods
    public static HttpResponse jsonResponse(Object data) {
        return new HttpResponse().status(200, "OK").json(data);
    }

    public static HttpResponse withStatus(int code, String message) {
        return new HttpResponse().status(code, message);
    }

    // Convenience methods for common responses
    public static HttpResponse ok() {
        return new HttpResponse().status(200, "OK");
    }

    public static HttpResponse ok(String body) {
        return new HttpResponse().status(200, "OK").body(body);
    }

    public static HttpResponse created(Object data) {
        return new HttpResponse().status(201, "Created").json(data);
    }

    public static HttpResponse noContent() {
        return new HttpResponse().status(204, "No Content");
    }

    public static HttpResponse badRequest(String message) {
        return new HttpResponse().status(400, "Bad Request").json(new ErrorResponse(message));
    }

    public static HttpResponse unauthorized(String message) {
        return new HttpResponse().status(401, "Unauthorized").json(new ErrorResponse(message));
    }

    public static HttpResponse forbidden(String message) {
        return new HttpResponse().status(403, "Forbidden").json(new ErrorResponse(message));
    }

    public static HttpResponse notFound(String message) {
        return new HttpResponse().status(404, "Not Found").json(new ErrorResponse(message));
    }

    public static HttpResponse methodNotAllowed(String message) {
        return new HttpResponse().status(405, "Method Not Allowed").json(new ErrorResponse(message));
    }

    public static HttpResponse conflict(String message) {
        return new HttpResponse().status(409, "Conflict").json(new ErrorResponse(message));
    }

    public static HttpResponse internalServerError(String message) {
        return new HttpResponse().status(500, "Internal Server Error").json(new ErrorResponse(message));
    }

    // Builder methods
    public HttpResponse status(int code, String message) {
        this.statusCode = code;
        this.statusMessage = message;
        return this;
    }

    public HttpResponse header(String name, String value) {
        return setHeader(name, value);
    }

    public HttpResponse setHeader(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public HttpResponse body(String body) {
        this.body = body;
        setHeader("Content-Type", "text/plain; charset=utf-8");
        setHeader("Content-Length", String.valueOf(this.body.length()));
        return this;
    }

    public HttpResponse body(byte[] body) {
        this.body = new String(body); // Convert byte array to string for body
        setHeader("Content-Type", "text/plain; charset=utf-8");
        setHeader("Content-Length", String.valueOf(this.body.length()));
        return this;
    }

    public HttpResponse html(String html) {
        this.body = html;
        setHeader("Content-Type", "text/html; charset=utf-8");
        setHeader("Content-Length", String.valueOf(this.body.length()));
        return this;
    }

    public HttpResponse json(Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            this.body = json;
            setHeader("Content-Type", "application/json; charset=utf-8");
            setHeader("Content-Length", String.valueOf(this.body.length()));
        } catch (JsonProcessingException e) {
            return internalServerError("Failed to serialize JSON response");
        }
        return this;
    }

    // CORS headers
    public HttpResponse cors() {
        setHeader("Access-Control-Allow-Origin", "*");
        setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        setHeader("Access-Control-Max-Age", "3600");
        return this;
    }

    public HttpResponse cors(String origin) {
        setHeader("Access-Control-Allow-Origin", origin);
        setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        setHeader("Access-Control-Max-Age", "3600");
        return this;
    }

    // Cache control
    public HttpResponse noCache() {
        setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        setHeader("Pragma", "no-cache");
        setHeader("Expires", "0");
        return this;
    }

    public HttpResponse cache(int maxAgeSeconds) {
        setHeader("Cache-Control", "public, max-age=" + maxAgeSeconds);
        return this;
    }

    // Send response to OutputStream
    public void send(OutputStream outputStream) throws IOException {
        // Status line
        String statusLine = "HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n";
        outputStream.write(statusLine.getBytes());

        // Headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String headerLine = header.getKey() + ": " + header.getValue() + "\r\n";
            outputStream.write(headerLine.getBytes());
        }

        // Empty line to separate headers from body
        outputStream.write("\r\n".getBytes());

        // Body
        if (body != null && !body.isEmpty()) {
            outputStream.write(body.getBytes());
        }

        outputStream.flush();
    }

    // Getters
    public int getStatusCode() { return statusCode; }
    public String getStatusMessage() { return statusMessage; }
    public Map<String, String> getHeaders() { return new HashMap<>(headers); }
    public String getBody() { return body; }

    @Override
    public String toString() {
        return String.format("HTTP/1.1 %d %s", statusCode, statusMessage);
    }

    // Inner class for error responses
    public static class ErrorResponse {
        private String error;
        private String timestamp;

        public ErrorResponse(String error) {
            this.error = error;
            this.timestamp = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        }

        public String getError() { return error; }
        public String getTimestamp() { return timestamp; }
    }

    // Inner class for success responses with data
    public static class SuccessResponse<T> {
        private boolean success;
        private T data;
        private String message;
        private String timestamp;

        public SuccessResponse(T data) {
            this.success = true;
            this.data = data;
            this.timestamp = Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        }

        public SuccessResponse(T data, String message) {
            this(data);
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public T getData() { return data; }
        public String getMessage() { return message; }
        public String getTimestamp() { return timestamp; }
    }

    // Helper method to create paginated response
    public static class PagedResponse<T> {
        private java.util.List<T> data;
        private int page;
        private int pageSize;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;

        public PagedResponse(java.util.List<T> data, int page, int pageSize, long totalElements) {
            this.data = data;
            this.page = page;
            this.pageSize = pageSize;
            this.totalElements = totalElements;
            this.totalPages = (int) Math.ceil((double) totalElements / pageSize);
            this.hasNext = page < totalPages - 1;
            this.hasPrevious = page > 0;
        }

        // Getters
        public java.util.List<T> getData() { return data; }
        public int getPage() { return page; }
        public int getPageSize() { return pageSize; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public boolean isHasNext() { return hasNext; }
        public boolean isHasPrevious() { return hasPrevious; }
    }
}