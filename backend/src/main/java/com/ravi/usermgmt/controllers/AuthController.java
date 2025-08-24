package com.ravi.usermgmt.controllers;

import com.ravi.usermgmt.db.UserRepository;
import com.ravi.usermgmt.http.HttpRequest;
import com.ravi.usermgmt.http.HttpResponse;
import com.ravi.usermgmt.models.User;
import com.ravi.usermgmt.utils.AuthUtils;
import com.ravi.usermgmt.utils.PasswordUtils;
import com.ravi.usermgmt.utils.GoogleOAuthService;
import com.ravi.usermgmt.utils.GoogleUserInfo;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final UserRepository userRepository;

    public AuthController() {
        this.userRepository = new UserRepository();
    }

    public CompletableFuture<HttpResponse> login(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LoginRequest loginRequest = request.bodyAs(LoginRequest.class);
                
                if (loginRequest.getEmail() == null || loginRequest.getEmail().trim().isEmpty() ||
                    loginRequest.getPassword() == null || loginRequest.getPassword().trim().isEmpty()) {
                    return HttpResponse.badRequest("Email and password are required");
                }

                // Find user by email
                Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail().trim());
                if (userOpt.isEmpty()) {
                    logger.warn("Login attempt with non-existent email: {}", loginRequest.getEmail());
                    return HttpResponse.unauthorized("Invalid credentials");
                }

                User user = userOpt.get();
                
                // Check if user is active
                if (user.getStatus() != User.UserStatus.ACTIVE) {
                    logger.warn("Login attempt with inactive user: {}", user.getEmail());
                    return HttpResponse.forbidden("Account is not active");
                }

                // Verify password
                if (!PasswordUtils.verifyPassword(loginRequest.getPassword(), user.getPasswordHash())) {
                    logger.warn("Login attempt with wrong password for user: {}", user.getEmail());
                    return HttpResponse.unauthorized("Invalid credentials");
                }

                // Generate JWT token
                String token = AuthUtils.generateJwtToken(user);
                
                // Update last login
                userRepository.updateLastLogin(user.getId());

                // Create response
                LoginResponse response = new LoginResponse(
                    token,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getRole(),
                    user.getStatus()
                );

                logger.info("Successful login for user: {}", user.getEmail());
                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(response, "Login successful"));

            } catch (IllegalArgumentException e) {
                logger.error("Invalid login request format: {}", e.getMessage());
                return HttpResponse.badRequest("Invalid request format: " + e.getMessage());
            } catch (SQLException e) {
                logger.error("Database error during login: {}", e.getMessage(), e);
                return HttpResponse.internalServerError("Login failed due to server error");
            } catch (Exception e) {
                logger.error("Unexpected error during login: {}", e.getMessage(), e);
                return HttpResponse.internalServerError("Login failed");
            }
        });
    }

    public CompletableFuture<HttpResponse> validateToken(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String authHeader = request.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return HttpResponse.unauthorized("Missing or invalid authorization header");
                }

                String token = authHeader.substring(7);
                Optional<AuthUtils.TokenClaims> claimsOpt = AuthUtils.validateJwtToken(token);
                
                if (claimsOpt.isEmpty()) {
                    return HttpResponse.unauthorized("Invalid or expired token");
                }

                AuthUtils.TokenClaims claims = claimsOpt.get();
                
                // Verify user still exists and is active
                Optional<User> userOpt = userRepository.findById(claims.getUserId());
                if (userOpt.isEmpty()) {
                    return HttpResponse.unauthorized("User not found");
                }

                User user = userOpt.get();
                if (user.getStatus() != User.UserStatus.ACTIVE) {
                    return HttpResponse.forbidden("Account is not active");
                }

                // Return user info
                UserInfo userInfo = new UserInfo(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getRole(),
                    user.getStatus()
                );

                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(userInfo, "Token valid"));

            } catch (SQLException e) {
                logger.error("Database error during token validation: {}", e.getMessage(), e);
                return HttpResponse.internalServerError("Token validation failed");
            } catch (Exception e) {
                logger.error("Error validating token: {}", e.getMessage(), e);
                return HttpResponse.unauthorized("Invalid token");
            }
        });
    }

    public CompletableFuture<HttpResponse> logout(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            // For JWT tokens, logout is typically handled client-side by discarding the token
            // In a production system, you might maintain a blacklist of revoked tokens
            logger.info("User logout request");
            return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(null, "Logged out successfully"));
        });
    }

    public CompletableFuture<HttpResponse> refreshToken(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String authHeader = request.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return HttpResponse.unauthorized("Missing or invalid authorization header");
                }

                String token = authHeader.substring(7);
                Optional<AuthUtils.TokenClaims> claimsOpt = AuthUtils.validateJwtToken(token);
                
                if (claimsOpt.isEmpty()) {
                    return HttpResponse.unauthorized("Invalid or expired token");
                }

                AuthUtils.TokenClaims claims = claimsOpt.get();
                
                // Check if token is close to expiry (refresh if less than 1 hour remaining)
                if (claims.getExpiresAt().isAfter(Instant.now().plus(1, ChronoUnit.HOURS))) {
                    return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(
                        new RefreshTokenResponse(token), "Token still valid"));
                }

                // Get fresh user data and generate new token
                Optional<User> userOpt = userRepository.findById(claims.getUserId());
                if (userOpt.isEmpty()) {
                    return HttpResponse.unauthorized("User not found");
                }

                User user = userOpt.get();
                if (user.getStatus() != User.UserStatus.ACTIVE) {
                    return HttpResponse.forbidden("Account is not active");
                }

                String newToken = AuthUtils.generateJwtToken(user);
                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(
                    new RefreshTokenResponse(newToken), "Token refreshed"));

            } catch (SQLException e) {
                logger.error("Database error during token refresh: {}", e.getMessage(), e);
                return HttpResponse.internalServerError("Token refresh failed");
            } catch (Exception e) {
                logger.error("Error refreshing token: {}", e.getMessage(), e);
                return HttpResponse.unauthorized("Token refresh failed");
            }
        });
    }

    /**
     * Google OAuth login endpoint
     */
    public CompletableFuture<HttpResponse> googleLogin(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GoogleLoginRequest googleRequest = request.bodyAs(GoogleLoginRequest.class);
                
                if (googleRequest == null || googleRequest.getToken() == null || googleRequest.getToken().trim().isEmpty()) {
                    return HttpResponse.badRequest("Google token is required");
                }

                // Verify Google token and extract user info
                GoogleOAuthService googleService = new GoogleOAuthService();
                GoogleUserInfo googleUser = googleService.verifyToken(googleRequest.getToken());
                
                logger.info("Google OAuth verification successful for user: {}", googleUser.getEmail());

                // Find or create user in our database
                User user = findOrCreateGoogleUser(googleUser);
                
                // Generate our JWT token
                String token = AuthUtils.generateJwtToken(user);
                
                // Create response (same format as regular login)
                LoginResponse response = new LoginResponse(
                    token,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getRole(),
                    user.getStatus()
                );
                
                logger.info("Google login successful for user: {}", user.getEmail());
                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(response, "Google login successful"));
                
            } catch (Exception e) {
                logger.error("Google login failed: {}", e.getMessage(), e);
                return HttpResponse.unauthorized("Google login failed: " + e.getMessage());
            }
        });
    }

    /**
     * Find existing user by email or create new user from Google info
     */
    private User findOrCreateGoogleUser(GoogleUserInfo googleUser) throws SQLException {
        // Try to find existing user by email
        Optional<User> existingUser = userRepository.findByEmail(googleUser.getEmail());
        
        if (existingUser.isPresent()) {
            // User exists - update Google ID if needed
            User user = existingUser.get();
            
            // If user was created with email/password, convert to Google OAuth
            if (user.getGoogleId() == null || !user.getGoogleId().equals(googleUser.getGoogleId())) {
                user.setGoogleId(googleUser.getGoogleId());
                user.setProfilePictureUrl(googleUser.getPictureUrl());
                user.setOauthProvider("google");
                userRepository.update(user);
                logger.info("Updated existing user {} with Google OAuth info", user.getEmail());
            }
            
            return user;
        } else {
            // Create new user from Google info
            String username = generateUsernameFromEmail(googleUser.getEmail());
            
            User newUser = new User.Builder()
                .username(username)
                .email(googleUser.getEmail())
                .firstName(googleUser.getFirstName() != null ? googleUser.getFirstName() : "")
                .lastName(googleUser.getLastName() != null ? googleUser.getLastName() : "")
                .googleId(googleUser.getGoogleId())
                .profilePictureUrl(googleUser.getPictureUrl())
                .oauthProvider("google")
                .role(User.UserRole.USER) // Default role for new Google users
                .status(User.UserStatus.ACTIVE)
                .build();
                
            User createdUser = userRepository.create(newUser);
            logger.info("Created new Google user: {}", createdUser.getEmail());
            return createdUser;
        }
    }

    /**
     * Generate a unique username from email address
     */
    private String generateUsernameFromEmail(String email) throws SQLException {
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
        String username = baseUsername;
        int counter = 1;
        
        // Ensure username is unique
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter++;
        }
        
        return username;
    }

    // DTO Classes
    public static class LoginRequest {
        public String email;
        public String password;

        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public void setEmail(String email) { this.email = email; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginResponse {
        public String token;
        public Long userId;
        public String username;
        public String email;
        public String firstName;
        public String lastName;
        public User.UserRole role;
        public User.UserStatus status;

        public LoginResponse(String token, Long userId, String username, String email, 
                           String firstName, String lastName, User.UserRole role, User.UserStatus status) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.role = role;
            this.status = status;
        }
    }

    public static class UserInfo {
        public Long userId;
        public String username;
        public String email;
        public String firstName;
        public String lastName;
        public User.UserRole role;
        public User.UserStatus status;

        public UserInfo(Long userId, String username, String email, 
                       String firstName, String lastName, User.UserRole role, User.UserStatus status) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.role = role;
            this.status = status;
        }
    }

    public static class RefreshTokenResponse {
        public String token;

        public RefreshTokenResponse(String token) {
            this.token = token;
        }
    }

    public static class GoogleLoginRequest {
        public String token;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
} 