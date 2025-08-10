package com.ravi.usermgmt.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * User model representing a user in the system
 */
public class User {
    private Long id;
    private String username;
    private String email;
    
    @JsonIgnore  // Never serialize password hash
    private String passwordHash;
    
    private String firstName;
    private String lastName;
    private String phone;
    private UserStatus status;
    private UserRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLogin;

    // Enums for status and role
    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }

    public enum UserRole {
        USER, ADMIN
    }

    // Default constructor
    public User() {
        this.status = UserStatus.ACTIVE;
        this.role = UserRole.USER;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Constructor for creating new users
    public User(String username, String email, String passwordHash, 
                String firstName, String lastName) {
        this();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    // Full constructor
    public User(Long id, String username, String email, String passwordHash,
                String firstName, String lastName, String phone, 
                UserStatus status, UserRole role, LocalDateTime createdAt,
                LocalDateTime updatedAt, LocalDateTime lastLogin) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLogin = lastLogin;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    // Utility methods
    @JsonProperty("fullName")
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    // Update the updated_at timestamp
    public void markAsUpdated() {
        this.updatedAt = LocalDateTime.now();
    }

    // Update last login timestamp
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
        this.markAsUpdated();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) ||
               (Objects.equals(username, user.username) && 
                Objects.equals(email, user.email));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, email);
    }

    @Override
    public String toString() {
        return "User{" +
               "id=" + id +
               ", username='" + username + '\'' +
               ", email='" + email + '\'' +
               ", firstName='" + firstName + '\'' +
               ", lastName='" + lastName + '\'' +
               ", status=" + status +
               ", role=" + role +
               ", createdAt=" + createdAt +
               '}';
    }

    // Builder pattern for easy object creation
    public static class Builder {
        private User user = new User();

        public Builder username(String username) {
            user.setUsername(username);
            return this;
        }

        public Builder email(String email) {
            user.setEmail(email);
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            user.setPasswordHash(passwordHash);
            return this;
        }

        public Builder firstName(String firstName) {
            user.setFirstName(firstName);
            return this;
        }

        public Builder lastName(String lastName) {
            user.setLastName(lastName);
            return this;
        }

        public Builder phone(String phone) {
            user.setPhone(phone);
            return this;
        }

        public Builder role(UserRole role) {
            user.setRole(role);
            return this;
        }

        public Builder status(UserStatus status) {
            user.setStatus(status);
            return this;
        }

        public User build() {
            return user;
        }
    }
}