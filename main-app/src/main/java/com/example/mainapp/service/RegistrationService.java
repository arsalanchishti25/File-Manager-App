package com.example.mainapp.service;


import com.example.mainapp.model.User;
import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.repository.MySQLUserRepository;
import com.example.mainapp.repository.UserRepository;
import com.example.mainapp.util.PasswordHasher;

public class RegistrationService {

    private final UserRepository userRepository;

    public RegistrationService() {
        RemoteMySQLDataSource remote = new RemoteMySQLDataSource();
        this.userRepository = new MySQLUserRepository(remote);
    }

    // still keep for tests if you want
    public RegistrationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String plainPassword, User.Role role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists.");
        }

        String passwordHash = PasswordHasher.hash(plainPassword);
        return userRepository.saveUserWithRole(username, passwordHash, role);
    }
}


