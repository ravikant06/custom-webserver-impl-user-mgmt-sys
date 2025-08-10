package com.ravi.usermgmt.http;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP Router for mapping requests to handlers
 */
public class HttpRouter {
    private static final Logger logger = Logger.getLogger(HttpRouter.class.getName());
    
    private final Map<String, Map<Pattern, RouteHandler>> routes;
    private RouteHandler notFoundHandler;
    private RouteHandler errorHandler;

    public HttpRouter() {
        this.routes = new HashMap<>();
        // Initialize route maps for each HTTP method
        this.routes.put("GET", new HashMap<>());
        this.routes.put("POST", new HashMap<>());
        this.routes.put("PUT", new HashMap<>());
        this.routes.put("DELETE", new HashMap<>());
        this.routes.put("PATCH", new HashMap<>());
        this.routes.put("OPTIONS", new HashMap<>());
        
        // Default handlers
        this.notFoundHandler = (request, pathParams) -> 
            CompletableFuture.completedFuture(HttpResponse.notFound("Endpoint not found"));
            
        this.errorHandler = (request, pathParams) -> 
            CompletableFuture.completedFuture(HttpResponse.internalServerError("Internal server error"));
    }

    // Route registration methods
    public HttpRouter get(String path, RouteHandler handler) {
        return addRoute("GET", path, handler);
    }

    public HttpRouter post(String path, RouteHandler handler) {
        return addRoute("POST", path, handler);
    }

    public HttpRouter put(String path, RouteHandler handler) {
        return addRoute("PUT", path, handler);
    }

    public HttpRouter delete(String path, RouteHandler handler) {
        return addRoute("DELETE", path, handler);
    }

    public HttpRouter patch(String path, RouteHandler handler) {
        return addRoute("PATCH", path, handler);
    }

    public HttpRouter options(String path, RouteHandler handler) {
        return addRoute("OPTIONS", path, handler);
    }

    private HttpRouter addRoute(String method, String path, RouteHandler handler) {
        Pattern pattern = pathToPattern(path);
        routes.get(method).put(pattern, handler);
        logger.info("Registered route: " + method + " " + path);
        return this;
    }

    // Set custom error handlers
    public HttpRouter notFound(RouteHandler handler) {
        this.notFoundHandler = handler;
        return this;
    }

    public HttpRouter error(RouteHandler handler) {
        this.errorHandler = handler;
        return this;
    }

    /**
     * Route an HTTP request to the appropriate handler
     */
    public CompletableFuture<HttpResponse> route(HttpRequest request) {
        try {
            String method = request.getMethod();
            String path = request.getPath();

            logger.info("Routing: " + method + " " + path);

            // Handle CORS preflight requests
            if ("OPTIONS".equals(method)) {
                return CompletableFuture.completedFuture(
                    HttpResponse.ok().cors().body("")
                );
            }

            // Find matching route
            Map<Pattern, RouteHandler> methodRoutes = routes.get(method);
            if (methodRoutes != null) {
                for (Map.Entry<Pattern, RouteHandler> entry : methodRoutes.entrySet()) {
                    Pattern pattern = entry.getKey();
                    Matcher matcher = pattern.matcher(path);
                    
                    if (matcher.matches()) {
                        // Extract path parameters
                        Map<String, String> pathParams = extractPathParams(pattern, matcher);
                        
                        // Call the handler
                        RouteHandler handler = entry.getValue();
                        return handler.handle(request, pathParams)
                            .exceptionally(throwable -> {
                                logger.severe("Error in route handler: " + throwable.getMessage());
                                return HttpResponse.internalServerError("Internal server error");
                            });
                    }
                }
            }

            // No route found
            return notFoundHandler.handle(request, new HashMap<>());
            
        } catch (Exception e) {
            logger.severe("Error routing request: " + e.getMessage());
            return CompletableFuture.completedFuture(
                HttpResponse.internalServerError("Internal server error")
            );
        }
    }

    /**
     * Convert a path pattern to a regex Pattern
     * Supports path parameters like /users/{id}
     */
    private Pattern pathToPattern(String path) {
        // Replace {param} with named capture groups
        String pattern = path.replaceAll("\\{([^}]+)\\}", "(?<$1>[^/]+)");
        
        // Ensure exact match
        pattern = "^" + pattern + "$";
        
        return Pattern.compile(pattern);
    }

    /**
     * Extract path parameters from the matched pattern
     */
    private Map<String, String> extractPathParams(Pattern pattern, Matcher matcher) {
        Map<String, String> params = new HashMap<>();
        
        // Get all named groups from the pattern
        String patternStr = pattern.pattern();
        Pattern namedGroupPattern = Pattern.compile("\\(\\?<([^>]+)>");
        Matcher namedGroupMatcher = namedGroupPattern.matcher(patternStr);
        
        int groupIndex = 1;
        while (namedGroupMatcher.find()) {
            String paramName = namedGroupMatcher.group(1);
            String paramValue = matcher.group(groupIndex++);
            params.put(paramName, paramValue);
        }
        
        return params;
    }

    /**
     * Create a middleware chain
     */
    public static RouteHandler withMiddleware(Middleware middleware, RouteHandler handler) {
        return (request, pathParams) -> {
            return middleware.process(request, pathParams, handler);
        };
    }

    /**
     * Create CORS middleware
     */
    public static Middleware corsMiddleware() {
        return corsMiddleware("*");
    }

    public static Middleware corsMiddleware(String allowedOrigin) {
        return (request, pathParams, next) -> {
            return next.handle(request, pathParams)
                .thenApply(response -> response.cors(allowedOrigin));
        };
    }

    /**
     * Create logging middleware
     */
    public static Middleware loggingMiddleware() {
        return (request, pathParams, next) -> {
            long startTime = System.currentTimeMillis();
            String requestInfo = request.getMethod() + " " + request.getPath();
            
            logger.info("Request started: " + requestInfo);
            
            return next.handle(request, pathParams)
                .thenApply(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.info("Request completed: " + requestInfo + 
                               " - " + response.getStatusCode() + " (" + duration + "ms)");
                    return response;
                });
        };
    }

    /**
     * Create authentication middleware
     */
    public static Middleware authMiddleware() {
        return (request, pathParams, next) -> {
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return CompletableFuture.completedFuture(
                    HttpResponse.unauthorized("Missing or invalid authorization header")
                );
            }
            
            // Extract token (in a real app, you'd validate this)
            String token = authHeader.substring(7);
            // TODO: Validate JWT token here
            
            return next.handle(request, pathParams);
        };
    }

    /**
     * Create rate limiting middleware (simple in-memory implementation)
     */
    public static Middleware rateLimitMiddleware(int requestsPerMinute) {
        Map<String, RateLimitBucket> buckets = new HashMap<>();
        
        return (request, pathParams, next) -> {
            String clientId = getClientId(request);
            RateLimitBucket bucket = buckets.computeIfAbsent(clientId, 
                k -> new RateLimitBucket(requestsPerMinute));
            
            if (!bucket.tryConsume()) {
                return CompletableFuture.completedFuture(
                    HttpResponse.withStatus(429, "Too Many Requests")
                        .json(new HttpResponse.ErrorResponse("Rate limit exceeded"))
                );
            }
            
            return next.handle(request, pathParams);
        };
    }

    private static String getClientId(HttpRequest request) {
        // In a real app, you might use IP address, user ID, API key, etc.
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null) {
            return xff.split(",")[0].trim();
        }
        return "unknown"; // In real implementation, get from socket
    }

    // Functional interfaces
    @FunctionalInterface
    public interface RouteHandler {
        CompletableFuture<HttpResponse> handle(HttpRequest request, Map<String, String> pathParams);
    }

    @FunctionalInterface
    public interface Middleware {
        CompletableFuture<HttpResponse> process(HttpRequest request, Map<String, String> pathParams, RouteHandler next);
    }

    // Simple rate limiting bucket
    private static class RateLimitBucket {
        private final int maxRequests;
        private final long windowMs = 60_000; // 1 minute
        private long windowStart;
        private int requestCount;

        public RateLimitBucket(int maxRequests) {
            this.maxRequests = maxRequests;
            this.windowStart = System.currentTimeMillis();
            this.requestCount = 0;
        }

        public synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            
            // Reset window if expired
            if (now - windowStart >= windowMs) {
                windowStart = now;
                requestCount = 0;
            }
            
            if (requestCount < maxRequests) {
                requestCount++;
                return true;
            }
            
            return false;
        }
    }
}