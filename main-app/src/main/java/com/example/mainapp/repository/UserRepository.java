package com.example.mainapp.repository;

import com.example.mainapp.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsernameAndPassword(String username, String password);

    /**
     * Save a new user with a hashed password.
     * Returns the newly created User with its ID.
     */
    User saveUser(String username, String passwordHash);

    /**
     * Check if a username already exists.
     */
    boolean existsByUsername(String username);
    
    Optional<User> findById(long id);

    List<User> findAll();

    void deleteById(long id);

    boolean updatePassword(String username, String newHashedPassword);

    void promoteDemotebyId(long id);
    // later: save, findById, listAll, etc.
    User saveUserWithRole(String username, String passwordHash, User.Role role);
}
