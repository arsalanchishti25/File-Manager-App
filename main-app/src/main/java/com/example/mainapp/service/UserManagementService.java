package com.example.mainapp.service;

import com.example.mainapp.model.User;
import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.repository.MySQLUserRepository;
import com.example.mainapp.repository.UserRepository;

import java.util.List;

public class UserManagementService {

    private final UserRepository userRepository;

    public UserManagementService() {
        RemoteMySQLDataSource remote = new RemoteMySQLDataSource();
        this.userRepository = new MySQLUserRepository(remote);
    }

    // keep this for DI/tests
    public UserManagementService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(long userId) {
        userRepository.deleteById(userId);
    }

    public void promoteDemoteUser(long userId) {
        userRepository.promoteDemotebyId(userId);
    }
}
