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


// public class RegistrationService {
//     private final UserRepository userRepository;

//     public RegistrationService() {
//         LocalSQLiteDataSource localDataSource = new LocalSQLiteDataSource();
//         this.userRepository = new SQLiteUserRepository(localDataSource);
//     }

//     // Constructor for DI / testing
//     public RegistrationService(UserRepository userRepository) {
//         this.userRepository = userRepository;
//     }

//     /**
//      * Register a new user.
//      * Returns the created User if successful, or throws an exception if username exists.
//      */
//     public User register(String username, String plainPassword, User.Role role) throws IllegalArgumentException {
//         // Validate input
//         if (username == null || username.isBlank()) {
//             throw new IllegalArgumentException("Username cannot be empty.");
//         }
//         // if (plainPassword == null || plainPassword.length() < 6) {
//         //     throw new IllegalArgumentException("Password must be at least 6 characters.");
//         // }

//         // Check if username already exists
//         if (userRepository.existsByUsername(username)) {
//             throw new IllegalArgumentException("Username already exists.");
//         }

//         // Hash the password and save
//         String passwordHash = PasswordHasher.hash(plainPassword);
//         return ((SQLiteUserRepository) userRepository).saveUserWithRole(username, passwordHash, role);
//     }
// }
