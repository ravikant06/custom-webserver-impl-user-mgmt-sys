package com.ravi.usermgmt.utils;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Service for handling Google OAuth token verification and user info extraction
 */
public class GoogleOAuthService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleOAuthService.class);
    
    // Replace with your actual Google Client ID
    private static final String CLIENT_ID = "10217611478-r6n5miarsidp97u82kt9fcqcle9ftcb3.apps.googleusercontent.com";
    
    private final GoogleIdTokenVerifier verifier;
    
    public GoogleOAuthService() {
        this.verifier = new GoogleIdTokenVerifier.Builder(
            new NetHttpTransport(),
            GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(CLIENT_ID))
            .build();
    }
    
    /**
     * Verify Google ID token and extract user information
     * @param idTokenString The ID token string from Google
     * @return GoogleUserInfo containing user details
     * @throws Exception if token verification fails
     */
    public GoogleUserInfo verifyToken(String idTokenString) throws Exception {
        logger.debug("Verifying Google ID token");
        
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                
                // Extract user information
                String googleId = payload.getSubject();
                String email = payload.getEmail();
                boolean emailVerified = payload.getEmailVerified();
                String name = (String) payload.get("name");
                String firstName = (String) payload.get("given_name");
                String lastName = (String) payload.get("family_name");
                String pictureUrl = (String) payload.get("picture");
                
                // Validate required fields
                if (googleId == null || email == null) {
                    throw new Exception("Missing required user information in Google token");
                }
                
                if (!emailVerified) {
                    throw new Exception("Google account email is not verified");
                }
                
                logger.info("Successfully verified Google token for user: {}", email);
                
                return new GoogleUserInfo(
                    googleId,
                    email,
                    name != null ? name : firstName + " " + lastName,
                    firstName,
                    lastName,
                    pictureUrl,
                    emailVerified
                );
                
            } else {
                throw new Exception("Invalid Google ID token");
            }
            
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Error verifying Google token: {}", e.getMessage());
            throw new Exception("Failed to verify Google token: " + e.getMessage());
        }
    }
    
    /**
     * Update the Google Client ID (useful for configuration)
     */
    public static String getClientId() {
        return CLIENT_ID;
    }
} 