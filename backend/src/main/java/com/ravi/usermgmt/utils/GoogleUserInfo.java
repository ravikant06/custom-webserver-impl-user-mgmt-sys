package com.ravi.usermgmt.utils;

/**
 * Data class to hold Google user information from OAuth response
 */
public class GoogleUserInfo {
    private final String googleId;
    private final String email;
    private final String name;
    private final String firstName;
    private final String lastName;
    private final String pictureUrl;
    private final boolean emailVerified;

    public GoogleUserInfo(String googleId, String email, String name, 
                         String firstName, String lastName, String pictureUrl, 
                         boolean emailVerified) {
        this.googleId = googleId;
        this.email = email;
        this.name = name;
        this.firstName = firstName;
        this.lastName = lastName;
        this.pictureUrl = pictureUrl;
        this.emailVerified = emailVerified;
    }

    // Getters
    public String getGoogleId() { return googleId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPictureUrl() { return pictureUrl; }
    public boolean isEmailVerified() { return emailVerified; }

    @Override
    public String toString() {
        return "GoogleUserInfo{" +
               "googleId='" + googleId + '\'' +
               ", email='" + email + '\'' +
               ", name='" + name + '\'' +
               ", firstName='" + firstName + '\'' +
               ", lastName='" + lastName + '\'' +
               ", emailVerified=" + emailVerified +
               '}';
    }
} 