package com.example.aggregator.service;

import com.example.aggregator.config.AggregatorAppConfig;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

/**
 * Handles AES-256 encryption and decryption of chunks.
 */
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    private final SecretKey configuredKey;

    public EncryptionService() {
        this(AggregatorAppConfig.fromEnv());
    }

    public EncryptionService(AggregatorAppConfig config) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(config.requireEncryptionKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid ENCRYPTION_KEY: expected Base64", e);
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("Invalid ENCRYPTION_KEY: decoded key must be 16, 24, or 32 bytes");
        }
        this.configuredKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Generate a new AES-256 key.
     * TODO: Use a proper key management system
     */
    public SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE);
        SecretKey key = keyGenerator.generateKey();
        
        System.out.println("[EncryptionService] Generated new AES-256 key");
        return key;
    }

    /**
     * Get a hardcoded key for testing.
     * TODO: Replace with proper key retrieval from secure storage
     */
    public SecretKey getConfiguredKey() {
        return configuredKey;
    }

    /**
     * Encrypt data with AES-256.
     */
    public byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(data);
        
        System.out.println("[EncryptionService] Encrypted " + data.length + 
                         " bytes → " + encrypted.length + " bytes");
        return encrypted;
    }

    /**
     * Decrypt data with AES-256.
     */
    public byte[] decrypt(byte[] encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decrypted = cipher.doFinal(encryptedData);
        
        System.out.println("[EncryptionService] Decrypted " + encryptedData.length + 
                         " bytes → " + decrypted.length + " bytes");
        return decrypted;
    }
}
