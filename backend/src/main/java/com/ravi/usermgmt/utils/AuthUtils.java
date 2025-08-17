package com.ravi.usermgmt.utils;

import com.ravi.usermgmt.models.User;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthUtils {
    private static final Logger logger = LoggerFactory.getLogger(AuthUtils.class);
    
    // Simple JWT-like token implementation (for demo purposes)
    // In production, use a proper JWT library like jjwt
    private static final String SECRET_KEY = "your-super-secret-key-change-in-production";
    private static final long TOKEN_VALIDITY_HOURS = 24;

    /**
     * Generate a JWT-like token for a user
     */
    public static String generateJwtToken(User user) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plus(TOKEN_VALIDITY_HOURS, ChronoUnit.HOURS);
            
            // Create payload (simplified JWT payload)
            String payload = user.getId() + ":" + user.getEmail() + ":" + 
                           user.getRole() + ":" + now.getEpochSecond() + ":" + expiry.getEpochSecond();
            
            // Create signature
            String signature = createSignature(payload);
            
            // Combine payload and signature
            String token = Base64.getEncoder().encodeToString(payload.getBytes()) + "." + signature;
            
            logger.debug("Generated token for user: {}", user.getEmail());
            return token;
            
        } catch (Exception e) {
            logger.error("Failed to generate token for user: {}", user.getEmail(), e);
            throw new RuntimeException("Token generation failed", e);
        }
    }

    /**
     * Validate and parse a JWT-like token
     */
    public static Optional<TokenClaims> validateJwtToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return Optional.empty();
            }
            
            // Split token into payload and signature
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                logger.warn("Invalid token format");
                return Optional.empty();
            }
            
            String payloadBase64 = parts[0];
            String signature = parts[1];
            
            // Decode payload
            String payload = new String(Base64.getDecoder().decode(payloadBase64));
            
            // Verify signature
            String expectedSignature = createSignature(payload);
            if (!MessageDigest.isEqual(signature.getBytes(), expectedSignature.getBytes())) {
                logger.warn("Invalid token signature");
                return Optional.empty();
            }
            
            // Parse payload
            String[] payloadParts = payload.split(":");
            if (payloadParts.length != 5) {
                logger.warn("Invalid token payload format");
                return Optional.empty();
            }
            
            Long userId = Long.parseLong(payloadParts[0]);
            String email = payloadParts[1];
            User.UserRole role = User.UserRole.valueOf(payloadParts[2]);
            Instant issuedAt = Instant.ofEpochSecond(Long.parseLong(payloadParts[3]));
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(payloadParts[4]));
            
            // Check expiry
            if (expiresAt.isBefore(Instant.now())) {
                logger.debug("Token expired for user: {}", email);
                return Optional.empty();
            }
            
            TokenClaims claims = new TokenClaims(userId, email, role, issuedAt, expiresAt);
            logger.debug("Valid token for user: {}", email);
            return Optional.of(claims);
            
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Create a signature for the payload
     */
    private static String createSignature(String payload) {
        try {
            String data = payload + SECRET_KEY;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Signature creation failed", e);
        }
    }

    /**
     * Extract user ID from Authorization header
     */
    public static Optional<Long> extractUserIdFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        
        String token = authHeader.substring(7);
        Optional<TokenClaims> claims = validateJwtToken(token);
        return claims.map(TokenClaims::getUserId);
    }

    /**
     * Check if user has required role
     */
    public static boolean hasRole(String authHeader, User.UserRole requiredRole) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String token = authHeader.substring(7);
        Optional<TokenClaims> claims = validateJwtToken(token);
        
        if (claims.isEmpty()) {
            return false;
        }
        
        User.UserRole userRole = claims.get().getRole();
        
        // Admin can access everything
        if (userRole == User.UserRole.ADMIN) {
            return true;
        }
        
        // Check specific role
        return userRole == requiredRole;
    }

    /**
     * Check if user is admin
     */
    public static boolean isAdmin(String authHeader) {
        return hasRole(authHeader, User.UserRole.ADMIN);
    }

    /**
     * Token claims data class
     */
    public static class TokenClaims {
        private final Long userId;
        private final String email;
        private final User.UserRole role;
        private final Instant issuedAt;
        private final Instant expiresAt;

        public TokenClaims(Long userId, String email, User.UserRole role, Instant issuedAt, Instant expiresAt) {
            this.userId = userId;
            this.email = email;
            this.role = role;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }

        public Long getUserId() { return userId; }
        public String getEmail() { return email; }
        public User.UserRole getRole() { return role; }
        public Instant getIssuedAt() { return issuedAt; }
        public Instant getExpiresAt() { return expiresAt; }
    }
} 