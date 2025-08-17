package com.ravi.usermgmt.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PasswordUtils {
    private static final Logger logger = LoggerFactory.getLogger(PasswordUtils.class);
    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;
    private static final int ITERATIONS = 10000;

    /**
     * Hash a password with a random salt
     */
    public static String hashPassword(String password) {
        try {
            // Generate random salt
            byte[] salt = generateSalt();
            
            // Hash password with salt
            byte[] hashedPassword = hashPasswordWithSalt(password, salt, ITERATIONS);
            
            // Combine salt + iterations + hash for storage
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hashedPassword);
            
            return saltBase64 + ":" + ITERATIONS + ":" + hashBase64;
            
        } catch (NoSuchAlgorithmException e) {
            logger.error("Password hashing algorithm not available: {}", e.getMessage());
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /**
     * Verify a password against a stored hash
     * Supports both BCrypt (existing data) and new hash format
     */
    public static boolean verifyPassword(String password, String storedHash) {
        try {
            // Check if it's a BCrypt hash (existing database format)
            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                // Use BCrypt verification for existing hashes
                return org.mindrot.jbcrypt.BCrypt.checkpw(password, storedHash);
            }
                
            // Handle new hash format with salt:iterations:hash
            if (storedHash.contains(":")) {
                // Parse stored hash
                String[] parts = storedHash.split(":");
                if (parts.length != 3) {
                    logger.warn("Invalid stored hash format");
                    return false;
                }
                
                byte[] salt = Base64.getDecoder().decode(parts[0]);
                int iterations = Integer.parseInt(parts[1]);
                byte[] storedPasswordHash = Base64.getDecoder().decode(parts[2]);
                
                // Hash the provided password with the same salt and iterations
                byte[] testHash = hashPasswordWithSalt(password, salt, iterations);
                
                // Compare hashes
                return MessageDigest.isEqual(storedPasswordHash, testHash);
            }
            
            // Fallback: simple hash comparison (legacy support)
            return simpleHash(password).equals(storedHash);
            
        } catch (Exception e) {
            logger.error("Password verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate a random salt
     */
    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Hash password with salt using PBKDF2-like approach
     */
    private static byte[] hashPasswordWithSalt(String password, byte[] salt, int iterations) 
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        
        // Add salt to password
        digest.update(salt);
        byte[] hash = digest.digest(password.getBytes());
        
        // Perform iterations
        for (int i = 1; i < iterations; i++) {
            digest.reset();
            hash = digest.digest(hash);
        }
        
        return hash;
    }

    /**
     * Simple hash for backward compatibility
     */
    private static String simpleHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(input.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available", e);
        }
    }

    /**
     * Check if password meets strength requirements
     */
    public static boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isLetterOrDigit(c)) hasSpecial = true;
        }
        
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
} 