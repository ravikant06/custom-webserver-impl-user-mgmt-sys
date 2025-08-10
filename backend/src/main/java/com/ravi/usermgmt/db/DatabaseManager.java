package com.ravi.usermgmt.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Simple database connection pool implementation
 * This is a custom implementation since we're not using external libraries
 */
public class DatabaseManager {
    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());
    
    private static DatabaseManager instance;
    private static final ReentrantLock instanceLock = new ReentrantLock();
    
    // Connection pool
    private final BlockingQueue<Connection> connectionPool;
    private final AtomicInteger activeConnections;
    
    // Configuration
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int maxPoolSize;
    private final int minPoolSize;
    private final long connectionTimeout;
    
    private volatile boolean isShutdown = false;

    private DatabaseManager() {
        // Load configuration from environment or use defaults
        this.jdbcUrl = System.getProperty("db.url", 
            "jdbc:postgresql://localhost:5433/usermgmt");
        this.username = System.getProperty("db.username", "postgres");
        this.password = System.getProperty("db.password", "password123");
        this.maxPoolSize = Integer.parseInt(System.getProperty("db.pool.max", "20"));
        this.minPoolSize = Integer.parseInt(System.getProperty("db.pool.min", "5"));
        this.connectionTimeout = Long.parseLong(System.getProperty("db.timeout", "30000"));
        
        this.connectionPool = new LinkedBlockingQueue<>(maxPoolSize);
        this.activeConnections = new AtomicInteger(0);
        
        // Initialize connection pool
        initializePool();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instanceLock.lock();
            try {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            } finally {
                instanceLock.unlock();
            }
        }
        return instance;
    }

    private void initializePool() {
        try {
            // Load PostgreSQL driver
            Class.forName("org.postgresql.Driver");
            
            // Create minimum number of connections
            for (int i = 0; i < minPoolSize; i++) {
                Connection conn = createNewConnection();
                if (conn != null) {
                    connectionPool.offer(conn);
                    activeConnections.incrementAndGet();
                }
            }
            
            logger.info("Database connection pool initialized with " + 
                       connectionPool.size() + " connections");
            
        } catch (ClassNotFoundException e) {
            logger.severe("PostgreSQL driver not found: " + e.getMessage());
            throw new RuntimeException("Database driver not available", e);
        }
    }

    private Connection createNewConnection() {
        try {
            Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
            conn.setAutoCommit(true); // Default to auto-commit for simplicity
            
            // Test the connection
            if (!conn.isValid(5)) {
                conn.close();
                return null;
            }
            
            return conn;
            
        } catch (SQLException e) {
            logger.severe("Failed to create database connection: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        if (isShutdown) {
            throw new SQLException("Database manager is shutdown");
        }

        Connection conn = connectionPool.poll();
        
        if (conn == null) {
            // No available connection, try to create a new one if under max limit
            if (activeConnections.get() < maxPoolSize) {
                conn = createNewConnection();
                if (conn != null) {
                    activeConnections.incrementAndGet();
                }
            }
        }
        
        if (conn == null) {
            // Still no connection, wait for one to become available
            try {
                conn = connectionPool.take(); // This will block until available
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for connection", e);
            }
        }
        
        // Validate connection before returning
        if (conn != null && !conn.isClosed() && conn.isValid(5)) {
            return conn;
        } else {
            // Connection is invalid, create a new one
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    logger.warning("Error closing invalid connection: " + e.getMessage());
                }
            }
            activeConnections.decrementAndGet();
            return getConnection(); // Recursive call to get a valid connection
        }
    }

    /**
     * Return a connection to the pool
     */
    public void releaseConnection(Connection conn) {
        if (conn == null || isShutdown) {
            return;
        }
        
        try {
            if (!conn.isClosed() && conn.isValid(5)) {
                // Reset connection state
                conn.setAutoCommit(true);
                conn.clearWarnings();
                
                // Return to pool
                if (!connectionPool.offer(conn)) {
                    // Pool is full, close the connection
                    conn.close();
                    activeConnections.decrementAndGet();
                }
            } else {
                // Connection is invalid, close it
                conn.close();
                activeConnections.decrementAndGet();
                
                // Create a replacement connection if below minimum
                if (activeConnections.get() < minPoolSize) {
                    Connection newConn = createNewConnection();
                    if (newConn != null) {
                        connectionPool.offer(newConn);
                        activeConnections.incrementAndGet();
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("Error releasing connection: " + e.getMessage());
            activeConnections.decrementAndGet();
        }
    }

    /**
     * Execute a query with automatic connection management
     */
    public <T> T executeQuery(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            try (var stmt = conn.prepareStatement(sql)) {
                // Set parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                try (var rs = stmt.executeQuery()) {
                    return handler.handle(rs);
                }
            }
        } finally {
            if (conn != null) {
                releaseConnection(conn);
            }
        }
    }

    /**
     * Execute an update with automatic connection management
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            try (var stmt = conn.prepareStatement(sql)) {
                // Set parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                return stmt.executeUpdate();
            }
        } finally {
            if (conn != null) {
                releaseConnection(conn);
            }
        }
    }

    /**
     * Execute an insert and return generated key
     */
    public Long executeInsert(String sql, Object... params) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            try (var stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                // Set parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Insert failed, no rows affected");
                }
                
                try (var generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("Insert failed, no ID obtained");
                    }
                }
            }
        } finally {
            if (conn != null) {
                releaseConnection(conn);
            }
        }
    }

    /**
     * Get pool statistics
     */
    public PoolStats getPoolStats() {
        return new PoolStats(
            activeConnections.get(),
            connectionPool.size(),
            maxPoolSize,
            minPoolSize
        );
    }

    /**
     * Test database connectivity
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(5);
        } catch (SQLException e) {
            logger.warning("Database connectivity test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Shutdown the connection pool
     */
    public void shutdown() {
        if (isShutdown) {
            return;
        }
        
        isShutdown = true;
        logger.info("Shutting down database connection pool...");
        
        // Close all connections in the pool
        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.warning("Error closing connection during shutdown: " + e.getMessage());
            }
        }
        
        logger.info("Database connection pool shutdown complete");
    }

    // Functional interface for result set handling
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(java.sql.ResultSet rs) throws SQLException;
    }

    // Pool statistics class
    public static class PoolStats {
        private final int totalConnections;
        private final int availableConnections;
        private final int maxPoolSize;
        private final int minPoolSize;

        public PoolStats(int totalConnections, int availableConnections, 
                        int maxPoolSize, int minPoolSize) {
            this.totalConnections = totalConnections;
            this.availableConnections = availableConnections;
            this.maxPoolSize = maxPoolSize;
            this.minPoolSize = minPoolSize;
        }

        public int getTotalConnections() { return totalConnections; }
        public int getAvailableConnections() { return availableConnections; }
        public int getActiveConnections() { return totalConnections - availableConnections; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getMinPoolSize() { return minPoolSize; }

        @Override
        public String toString() {
            return String.format("PoolStats{total=%d, available=%d, active=%d, max=%d, min=%d}",
                totalConnections, availableConnections, getActiveConnections(), maxPoolSize, minPoolSize);
        }
    }
}