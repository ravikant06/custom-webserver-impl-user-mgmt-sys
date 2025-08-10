package com.ravi.usermgmt.controllers;

import com.ravi.usermgmt.db.UserRepository;
import com.ravi.usermgmt.http.HttpRequest;
import com.ravi.usermgmt.http.HttpResponse;
import com.ravi.usermgmt.models.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * User management REST API controller
 */
public class UserController {
    private static final Logger logger = Logger.getLogger(UserController.class.getName());
    private final UserRepository userRepository;

    public UserController() {
        this.userRepository = new UserRepository();
    }

    /**
     * GET /api/users - Get all users with pagination
     */
    public CompletableFuture<HttpResponse> getAllUsers(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Parse query parameters
                int page = parseIntParam(request.getQueryParam("page"), 0);
                int pageSize = parseIntParam(request.getQueryParam("pageSize"), 10);
                String search = request.getQueryParam("search");
                String status = request.getQueryParam("status");

                // Validate pagination parameters
                if (page < 0) page = 0;
                if (pageSize < 1 || pageSize > 100) pageSize = 10;

                List<User> users;
                long totalElements;

                if (search != null && !search.trim().isEmpty()) {
                    // Search users
                    users = userRepository.searchUsers(search.trim(), page, pageSize);
                    totalElements = userRepository.countSearchResults(search.trim());
                } else if (status != null && !status.trim().isEmpty()) {
                    // Filter by status
                    try {
                        User.UserStatus userStatus = User.UserStatus.valueOf(status.toUpperCase());
                        users = userRepository.findByStatus(userStatus);
                        totalElements = userRepository.countUsersByStatus(userStatus);
                        
                        // Apply pagination manually for filtered results
                        int fromIndex = page * pageSize;
                        int toIndex = Math.min(fromIndex + pageSize, users.size());
                        if (fromIndex < users.size()) {
                            users = users.subList(fromIndex, toIndex);
                        } else {
                            users = List.of();
                        }
                    } catch (IllegalArgumentException e) {
                        return HttpResponse.badRequest("Invalid status value: " + status);
                    }
                } else {
                    // Get all users
                    users = userRepository.findAll(page, pageSize);
                    totalElements = userRepository.countUsers();
                }

                // Create paginated response
                HttpResponse.PagedResponse<User> pagedResponse = 
                    new HttpResponse.PagedResponse<>(users, page, pageSize, totalElements);

                return HttpResponse.jsonResponse(pagedResponse);

            } catch (SQLException e) {
                logger.severe("Error fetching users: " + e.getMessage());
                return HttpResponse.internalServerError("Failed to fetch users");
            } catch (Exception e) {
                logger.severe("Unexpected error: " + e.getMessage());
                return HttpResponse.internalServerError("Internal server error");
            }
        });
    }

    /**
     * GET /api/users/{id} - Get user by ID
     */
    public CompletableFuture<HttpResponse> getUserById(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String idStr = pathParams.get("id");
                if (idStr == null) {
                    return HttpResponse.badRequest("User ID is required");
                }

                Long id;
                try {
                    id = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    return HttpResponse.badRequest("Invalid user ID format");
                }

                Optional<User> userOpt = userRepository.findById(id);
                if (userOpt.isEmpty()) {
                    return HttpResponse.notFound("User not found");
                }

                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(userOpt.get()));

            } catch (SQLException e) {
                logger.severe("Error fetching user: " + e.getMessage());
                return HttpResponse.internalServerError("Failed to fetch user");
            } catch (Exception e) {
                logger.severe("Unexpected error: " + e.getMessage());
                return HttpResponse.internalServerError("Internal server error");
            }
        });
    }

    /**
     * POST /api/users - Create new user
     */
    public CompletableFuture<HttpResponse> createUser(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Parse request body
                CreateUserRequest createRequest = request.getJsonBody(CreateUserRequest.class);
                if (createRequest == null) {
                    return HttpResponse.badRequest("Request body is required");
                }

                // Validate request
                String validation = validateCreateUserRequest(createRequest);
                if (validation != null) {
                    return HttpResponse.badRequest(validation);
                }

                // Check if username or email already exists
                if (userRepository.existsByUsername(createRequest.getUsername())) {
                    return HttpResponse.conflict("Username already exists");
                }
                if (userRepository.existsByEmail(createRequest.getEmail())) {
                    return HttpResponse.conflict("Email already exists");
                }

                // Create user
                User user = new User.Builder()
                    .username(createRequest.getUsername())
                    .email(createRequest.getEmail())
                    .passwordHash(createRequest.getPassword()) // Will be hashed in repository
                    .firstName(createRequest.getFirstName())
                    .lastName(createRequest.getLastName())
                    .phone(createRequest.getPhone())
                    .role(createRequest.getRole() != null ? createRequest.getRole() : User.UserRole.USER)
                    .build();

                User createdUser = userRepository.create(user);

                return HttpResponse.created(new HttpResponse.SuccessResponse<>(createdUser, "User created successfully"));

            } catch (SQLException e) {
                logger.severe("Error creating user: " + e.getMessage());
                return HttpResponse.internalServerError("Failed to create user");
            } catch (Exception e) {
                logger.severe("Unexpected error: " + e.getMessage());
                return HttpResponse.internalServerError("Internal server error");
            }
        });
    }

    /**
     * PUT /api/users/{id} - Update user
     */
    public CompletableFuture<HttpResponse> updateUser(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String idStr = pathParams.get("id");
                if (idStr == null) {
                    return HttpResponse.badRequest("User ID is required");
                }

                Long id;
                try {
                    id = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    return HttpResponse.badRequest("Invalid user ID format");
                }

                // Parse request body
                UpdateUserRequest updateRequest = request.getJsonBody(UpdateUserRequest.class);
                if (updateRequest == null) {
                    return HttpResponse.badRequest("Request body is required");
                }

                // Check if user exists
                Optional<User> existingUserOpt = userRepository.findById(id);
                if (existingUserOpt.isEmpty()) {
                    return HttpResponse.notFound("User not found");
                }

                User existingUser = existingUserOpt.get();

                // Validate request
                String validation = validateUpdateUserRequest(updateRequest);
                if (validation != null) {
                    return HttpResponse.badRequest(validation);
                }

                // Check for conflicts with other users
                if (!existingUser.getUsername().equals(updateRequest.getUsername()) && 
                    userRepository.existsByUsername(updateRequest.getUsername())) {
                    return HttpResponse.conflict("Username already exists");
                }
                if (!existingUser.getEmail().equals(updateRequest.getEmail()) && 
                    userRepository.existsByEmail(updateRequest.getEmail())) {
                    return HttpResponse.conflict("Email already exists");
                }

                // Update user fields
                existingUser.setUsername(updateRequest.getUsername());
                existingUser.setEmail(updateRequest.getEmail());
                existingUser.setFirstName(updateRequest.getFirstName());
                existingUser.setLastName(updateRequest.getLastName());
                existingUser.setPhone(updateRequest.getPhone());
                if (updateRequest.getRole() != null) {
                    existingUser.setRole(updateRequest.getRole());
                }
                if (updateRequest.getStatus() != null) {
                    existingUser.setStatus(updateRequest.getStatus());
                }

                User updatedUser = userRepository.update(existingUser);

                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(updatedUser, "User updated successfully"));

            } catch (SQLException e) {
                logger.severe("Error updating user: " + e.getMessage());
                return HttpResponse.internalServerError("Failed to update user");
            } catch (Exception e) {
                logger.severe("Unexpected error: " + e.getMessage());
                return HttpResponse.internalServerError("Internal server error");
            }
        });
    }

    /**
     * DELETE /api/users/{id} - Delete user
     */
    public CompletableFuture<HttpResponse> deleteUser(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String idStr = pathParams.get("id");
                if (idStr == null) {
                    return HttpResponse.badRequest("User ID is required");
                }

                Long id;
                try {
                    id = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    return HttpResponse.badRequest("Invalid user ID format");
                }

                boolean deleted = userRepository.delete(id);
                if (!deleted) {
                    return HttpResponse.notFound("User not found");
                }

                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(null, "User deleted successfully"));

            } catch (SQLException e) {
                logger.severe("Error deleting user: " + e.getMessage());
                return HttpResponse.internalServerError("Failed to delete user");
            } catch (Exception e) {
                logger.severe("Unexpected error: " + e.getMessage());
                return HttpResponse.internalServerError("Internal server error");
            }
        });
    }

    /**
     * PATCH /api/users/{id}/status - Update user status
     */
    public CompletableFuture<HttpResponse> updateUserStatus(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String idStr = pathParams.get("id");
                if (idStr == null) {
                    return HttpResponse.badRequest("User ID is required");
                }

                Long id;
                try {
                    id = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    return HttpResponse.badRequest("Invalid user ID format");
                }

                // Parse request body
                StatusUpdateRequest statusRequest = request.getJsonBody(StatusUpdateRequest.class);
                if (statusRequest == null || statusRequest.getStatus() == null) {
                    return HttpResponse.badRequest("Status is required");
                }

                // Check if user exists
                Optional<User> userOpt = userRepository.findById(id);
                if (userOpt.isEmpty()) {
                    return HttpResponse.notFound("User not found");
                }

                userRepository.updateStatus(id, statusRequest.getStatus());

                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(null, "User status updated successfully"));

            } catch (SQLException e) {
                logger.severe("Error updating user status: " + e.getMessage());
                return HttpResponse.internalServerError("Failed to update user status");
            } catch (Exception e) {
                logger.severe("Unexpected error: " + e.getMessage());
                return HttpResponse.internalServerError("Internal server error");
            }
        });
    }

    /**
     * GET /api/users/stats - Get user statistics
     */
    public CompletableFuture<HttpResponse> getUserStats(HttpRequest request, Map<String, String> pathParams) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long totalUsers = userRepository.countUsers();
                long activeUsers = userRepository.countUsersByStatus(User.UserStatus.ACTIVE);
                long inactiveUsers = userRepository.countUsersByStatus(User.UserStatus.INACTIVE);
                long suspendedUsers = userRepository.countUsersByStatus(User.UserStatus.SUSPENDED);

                UserStats stats = new UserStats(totalUsers, activeUsers, inactiveUsers, suspendedUsers);

                return HttpResponse.jsonResponse(new HttpResponse.SuccessResponse<>(stats));

            } catch (SQLException e) {
                logger.severe("Error fetching user stats: " + e.getMessage());
                return HttpResponse.internalServerError("Failed to fetch user statistics");
            } catch (Exception e) {
                logger.severe("Unexpected error: " + e.getMessage());
                return HttpResponse.internalServerError("Internal server error");
            }
        });
    }

    // Utility methods
    private int parseIntParam(String param, int defaultValue) {
        if (param == null || param.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(param.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String validateCreateUserRequest(CreateUserRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return "Username is required";
        }
        if (request.getUsername().length() < 3 || request.getUsername().length() > 50) {
            return "Username must be between 3 and 50 characters";
        }
        if (!request.getUsername().matches("^[a-zA-Z0-9_]+$")) {
            return "Username can only contain letters, numbers, and underscores";
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return "Email is required";
        }
        if (!isValidEmail(request.getEmail())) {
            return "Invalid email format";
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            return "Password must be at least 8 characters long";
        }

        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            return "First name is required";
        }
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            return "Last name is required";
        }

        return null; // Valid
    }

    private String validateUpdateUserRequest(UpdateUserRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return "Username is required";
        }
        if (request.getUsername().length() < 3 || request.getUsername().length() > 50) {
            return "Username must be between 3 and 50 characters";
        }
        if (!request.getUsername().matches("^[a-zA-Z0-9_]+$")) {
            return "Username can only contain letters, numbers, and underscores";
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return "Email is required";
        }
        if (!isValidEmail(request.getEmail())) {
            return "Invalid email format";
        }

        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            return "First name is required";
        }
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            return "Last name is required";
        }

        return null; // Valid
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    // Request/Response DTOs
    public static class CreateUserRequest {
        private String username;
        private String email;
        private String password;
        private String firstName;
        private String lastName;
        private String phone;
        private User.UserRole role;

        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public User.UserRole getRole() { return role; }
        public void setRole(User.UserRole role) { this.role = role; }
    }

    public static class UpdateUserRequest {
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private User.UserRole role;
        private User.UserStatus status;

        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public User.UserRole getRole() { return role; }
        public void setRole(User.UserRole role) { this.role = role; }

        public User.UserStatus getStatus() { return status; }
        public void setStatus(User.UserStatus status) { this.status = status; }
    }

    public static class StatusUpdateRequest {
        private User.UserStatus status;

        public User.UserStatus getStatus() { return status; }
        public void setStatus(User.UserStatus status) { this.status = status; }
    }

    public static class UserStats {
        private long totalUsers;
        private long activeUsers;
        private long inactiveUsers;
        private long suspendedUsers;

        public UserStats(long totalUsers, long activeUsers, long inactiveUsers, long suspendedUsers) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.inactiveUsers = inactiveUsers;
            this.suspendedUsers = suspendedUsers;
        }

        // Getters
        public long getTotalUsers() { return totalUsers; }
        public long getActiveUsers() { return activeUsers; }
        public long getInactiveUsers() { return inactiveUsers; }
        public long getSuspendedUsers() { return suspendedUsers; }
    }
}