package com.ravi.usermgmt;

import com.ravi.usermgmt.controllers.UserController;
import com.ravi.usermgmt.db.DatabaseManager;
import com.ravi.usermgmt.http.HttpRequest;
import com.ravi.usermgmt.http.HttpResponse;
import com.ravi.usermgmt.http.HttpRouter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// SLF4J logging imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure Java HTTP Web Server with threading support
 */
public class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
    
    // Server configuration
    private final int port;
    private final String host;
    private final int maxThreads;
    private final int socketTimeout;
    
    // Server components
    private ServerSocket serverSocket;
    private ExecutorService requestExecutor;
    private ExecutorService workerExecutor;
    private HttpRouter router;
    private DatabaseManager dbManager;
    
    // Server state
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    
    // Controllers
    private UserController userController;
    private com.ravi.usermgmt.controllers.AuthController authController;

    public WebServer() {
        this(8080, "localhost", 50, 30000);
    }

    public WebServer(int port, String host, int maxThreads, int socketTimeout) {
        this.port = port;
        this.host = host;
        this.maxThreads = maxThreads;
        this.socketTimeout = socketTimeout;
        
        initialize();
    }

    private void initialize() {
        logger.info("Initializing Web Server on {}:{}", host, port);
        logger.debug("Configuration: maxThreads={}, socketTimeout={}ms", maxThreads, socketTimeout);
        
        // Initialize thread pools
        this.requestExecutor = Executors.newFixedThreadPool(maxThreads, r -> {
            Thread t = new Thread(r, "RequestHandler-" + requestCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        logger.info("Created thread pool with {} threads", maxThreads);
        
        this.workerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Worker-" + System.currentTimeMillis());

            
            t.setDaemon(true);
            return t;
        });
        
        // Initialize database manager
        this.dbManager = DatabaseManager.getInstance();
        
        // Initialize controllers
        this.userController = new UserController();
        this.authController = new com.ravi.usermgmt.controllers.AuthController();
        
        // Initialize router and routes
        setupRoutes();
        
        logger.info("Web Server initialized successfully");
    }

    private void setupRoutes() {
        this.router = new HttpRouter();
        
        // Add middleware
        HttpRouter.Middleware corsMiddleware = HttpRouter.corsMiddleware("https://ravikant06.github.io");
        HttpRouter.Middleware loggingMiddleware = HttpRouter.loggingMiddleware();
        
        // Health check endpoint
        router.get("/health", HttpRouter.withMiddleware(corsMiddleware, 
            HttpRouter.withMiddleware(loggingMiddleware, this::healthCheck)));
        
        // API info endpoint
        router.get("/api/info", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, this::apiInfo)));
        
        // Database health check
        router.get("/api/health/db", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, this::databaseHealth)));
        
        // Server metrics
        router.get("/metrics", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, this::serverMetrics)));
        
        // Authentication endpoints
        router.post("/api/auth/login", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, authController::login)));
        
        router.post("/api/auth/logout", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, authController::logout)));
        
        router.get("/api/auth/validate", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, authController::validateToken)));
        
        router.post("/api/auth/refresh", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, authController::refreshToken)));
        
        router.post("/api/auth/google", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, authController::googleLogin)));
        
        // User management endpoints
        router.get("/api/users", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, userController::getAllUsers)));
        
        router.get("/api/users/{id}", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, userController::getUserById)));
        
        router.post("/api/users", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, userController::createUser)));
        
        router.put("/api/users/{id}", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, userController::updateUser)));
        
        router.delete("/api/users/{id}", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, userController::deleteUser)));
        
        router.patch("/api/users/{id}/status", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, userController::updateUserStatus)));
        
        router.get("/api/users/stats", HttpRouter.withMiddleware(corsMiddleware,
            HttpRouter.withMiddleware(loggingMiddleware, userController::getUserStats)));
        
        // Static file serving (simple implementation)
        router.get("/", HttpRouter.withMiddleware(corsMiddleware, this::serveIndex));
        router.get("/favicon.ico", HttpRouter.withMiddleware(corsMiddleware, this::serveFavicon));
        
        // Handle OPTIONS requests for CORS preflight
        router.options(".*", HttpRouter.withMiddleware(corsMiddleware, this::handleOptions));
        
        // Custom error handlers
        router.notFound((request, pathParams) -> 
            CompletableFuture.completedFuture(HttpResponse.notFound("Endpoint not found: " + request.getPath())));
        
        logger.info("Routes configured successfully");
    }

    public void start() throws IOException {
        if (isRunning.get()) {
            logger.warn("Server is already running");
            return;
        }
        
        logger.info("Starting Web Server on " + host + ":" + port);
        
        // Create server socket
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.setSoTimeout(socketTimeout);
        serverSocket.bind(new InetSocketAddress(host, port));
        
        isRunning.set(true);
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        logger.info("✅ Server started successfully on http://" + host + ":" + port);
        logger.info("📊 Database pool: " + dbManager.getPoolStats());
        logger.info("🔧 Max threads: " + maxThreads);
        logger.info("⏱️  Socket timeout: " + socketTimeout + "ms");
        
        // Main server loop
        while (isRunning.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                activeConnections.incrementAndGet();
                
                // Handle request asynchronously
                requestExecutor.submit(() -> handleClient(clientSocket));
                
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue
            } catch (IOException e) {
                if (isRunning.get()) {
                    logger.error("Error accepting client connection: " + e.getMessage());
                }
            }
        }
        
        logger.info("Server main loop ended");
    }

    private void handleClient(Socket clientSocket) {
        long requestId = requestCounter.incrementAndGet();
        
        try {
            clientSocket.setSoTimeout(socketTimeout);
            logger.debug("Handling request #" + requestId + " from " + clientSocket.getRemoteSocketAddress());
            
            // Parse HTTP request
            HttpRequest request = HttpRequest.parse(clientSocket.getInputStream());
            logger.debug("Parsed request: " + request);
            
            // Route request and get response
            CompletableFuture<HttpResponse> responseFuture = router.route(request);
            
            // Handle response asynchronously
            responseFuture
                .thenAcceptAsync(response -> {
                    try {
                        // Add CORS headers for cross-origin requests
                        response.cors("https://ravikant06.github.io");
                        
                        // Send response
                        response.send(clientSocket.getOutputStream());
                        logger.debug("Sent response for request #" + requestId + ": " + response.getStatusCode());
                    } catch (IOException e) {
                        logger.warn("Error sending response for request #" + requestId + ": " + e.getMessage());
                    }
                }, workerExecutor)
                .whenComplete((result, throwable) -> {
                    // Clean up
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        logger.debug("Error closing client socket: " + e.getMessage());
                    }
                    activeConnections.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.warn("Error handling request #" + requestId + ": " + throwable.getMessage());
                    }
                });
                
        } catch (Exception e) {
            logger.warn("Error handling client request #" + requestId + ": " + e.getMessage());
            
            try {
                // Send error response
                HttpResponse errorResponse = HttpResponse.internalServerError("Internal server error");
                errorResponse.cors("https://ravikant06.github.io");
                errorResponse.send(clientSocket.getOutputStream());
            } catch (IOException ioException) {
                logger.debug("Could not send error response: " + ioException.getMessage());
            }
            
            try {
                clientSocket.close();
            } catch (IOException ioException) {
                logger.debug("Error closing client socket: " + ioException.getMessage());
            }
            
            activeConnections.decrementAndGet();
        }
    }

    public void shutdown() {
        if (!isRunning.get()) {
            return;
        }
        
        logger.info("🛑 Shutting down Web Server...");
        isRunning.set(false);
        
        try {
            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                logger.info("Server socket closed");
            }
            
            // Shutdown thread pools
            logger.info("Shutting down thread pools...");
            requestExecutor.shutdown();
            workerExecutor.shutdown();
            
            try {
                if (!requestExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    requestExecutor.shutdownNow();
                }
                if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                requestExecutor.shutdownNow();
                workerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // Shutdown database manager
            dbManager.shutdown();
            
            logger.info("✅ Web Server shutdown complete");
            
        } catch (Exception e) {
            logger.error("Error during shutdown: " + e.getMessage());
        }
    }

    // Built-in endpoint handlers
    private CompletableFuture<HttpResponse> healthCheck(HttpRequest request, java.util.Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            HealthStatus health = new HealthStatus();
            health.status = "UP";
            health.timestamp = java.time.LocalDateTime.now().toString();
            health.uptime = System.currentTimeMillis(); // Simplified
            health.activeConnections = activeConnections.get();
            health.totalRequests = requestCounter.get();
            
                            return HttpResponse.jsonResponse(health);
        });
    }

    private CompletableFuture<HttpResponse> apiInfo(HttpRequest request, java.util.Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            ApiInfo info = new ApiInfo();
            info.name = "User Management API";
            info.version = "1.0.0";
            info.description = "Pure Java REST API for user management";
            info.author = "kumar.ravee101@gmail.com";
            
            return HttpResponse.jsonResponse(info);
        });
    }

    private CompletableFuture<HttpResponse> databaseHealth(HttpRequest request, java.util.Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isHealthy = dbManager.testConnection();
            DatabaseManager.PoolStats poolStats = dbManager.getPoolStats();
            
            DatabaseHealth dbHealth = new DatabaseHealth();
            dbHealth.status = isHealthy ? "UP" : "DOWN";
            dbHealth.poolStats = poolStats;
            
            if (isHealthy) {
                return HttpResponse.jsonResponse(dbHealth);
            } else {
                return HttpResponse.withStatus(503, "Service Unavailable").json(dbHealth);
            }
        });
    }

    private CompletableFuture<HttpResponse> serverMetrics(HttpRequest request, java.util.Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            ServerMetrics metrics = new ServerMetrics();
            metrics.activeConnections = activeConnections.get();
            metrics.totalRequests = requestCounter.get();
            metrics.maxThreads = maxThreads;
            metrics.dbPoolStats = dbManager.getPoolStats();
            
            Runtime runtime = Runtime.getRuntime();
            metrics.memoryUsed = runtime.totalMemory() - runtime.freeMemory();
            metrics.memoryTotal = runtime.totalMemory();
            metrics.memoryMax = runtime.maxMemory();
            
            return HttpResponse.jsonResponse(metrics);
        });
    }

    private CompletableFuture<HttpResponse> serveIndex(HttpRequest request, java.util.Map<String, String> pathParams) {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>User Management API</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; }
                    .endpoint { background: #f5f5f5; padding: 10px; margin: 10px 0; border-radius: 5px; }
                    .method { font-weight: bold; color: #2196F3; }
                </style>
            </head>
            <body>
                <h1>User Management API</h1>
                <p>Pure Java REST API Server</p>
                
                <h2>Available Endpoints:</h2>
                <div class="endpoint"><span class="method">GET</span> /health - Health check</div>
                <div class="endpoint"><span class="method">GET</span> /api/info - API information</div>
                <div class="endpoint"><span class="method">GET</span> /api/health/db - Database health</div>
                <div class="endpoint"><span class="method">GET</span> /metrics - Server metrics</div>
                <div class="endpoint"><span class="method">GET</span> /api/users - Get all users</div>
                <div class="endpoint"><span class="method">GET</span> /api/users/{id} - Get user by ID</div>
                <div class="endpoint"><span class="method">POST</span> /api/users - Create user</div>
                <div class="endpoint"><span class="method">PUT</span> /api/users/{id} - Update user</div>
                <div class="endpoint"><span class="method">DELETE</span> /api/users/{id} - Delete user</div>
                <div class="endpoint"><span class="method">PATCH</span> /api/users/{id}/status - Update user status</div>
                <div class="endpoint"><span class="method">GET</span> /api/users/stats - User statistics</div>
                
                <p><a href="/api/users">Try the API</a></p>
            </body>
            </html>
            """;
        return CompletableFuture.completedFuture(HttpResponse.ok().html(html));
    }

    private CompletableFuture<HttpResponse> serveFavicon(HttpRequest request, java.util.Map<String, String> pathParams) {
        return CompletableFuture.completedFuture(HttpResponse.notFound("Favicon not found"));
    }
    
    private CompletableFuture<HttpResponse> handleOptions(HttpRequest request, java.util.Map<String, String> pathParams) {
        return CompletableFuture.completedFuture(HttpResponse.ok().cors("https://ravikant06.github.io"));
    }

    // Main method
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            int port = 8080;
            String host = "localhost";
            
            if (args.length >= 1) {
                port = Integer.parseInt(args[0]);
            }
            if (args.length >= 2) {
                host = args[1];
            }
            
            // Create and start server
            WebServer server = new WebServer(port, host, 50, 30000);
            server.start();
            
        } catch (Exception e) {
            logger.error("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Response DTOs
    private static class HealthStatus {
        public String status;
        public String timestamp;
        public long uptime;
        public long activeConnections;
        public long totalRequests;
    }

    private static class ApiInfo {
        public String name;
        public String version;
        public String description;
        public String author;
    }

    private static class DatabaseHealth {
        public String status;
        public DatabaseManager.PoolStats poolStats;
    }

    private static class ServerMetrics {
        public long activeConnections;
        public long totalRequests;
        public int maxThreads;
        public DatabaseManager.PoolStats dbPoolStats;
        public long memoryUsed;
        public long memoryTotal;
        public long memoryMax;
    }
}