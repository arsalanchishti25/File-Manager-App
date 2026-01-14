package com.example.mainapp.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {

    /**
     * Hash a plain-text password using BCrypt.
     * Safe to store in the database.
     */
    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    /**
     * Verify a plain-text password against a stored hash.
     * Returns true if they match, false otherwise.
     */
    public static boolean verify(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}
