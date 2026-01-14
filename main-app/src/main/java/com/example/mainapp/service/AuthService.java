package com.example.mainapp.service;

//import com.example.filemanager.db.LocalSQLiteDataSource;
import com.example.mainapp.model.User;
//import com.example.filemanager.repository.SQLiteUserRepository;
import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.repository.MySQLUserRepository;
import com.example.mainapp.repository.UserRepository;

import java.util.Optional;

public class AuthService {

    private final UserRepository userRepository;

    public AuthService() {
        RemoteMySQLDataSource remote = new RemoteMySQLDataSource();
        this.userRepository = new MySQLUserRepository(remote);
    }

    // keep this for tests if you like
    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> login(String username, String password) {
        return userRepository.findByUsernameAndPassword(username, password);
    }

    public boolean updatePassword(String username, String newHashedPassword) {
        return userRepository.updatePassword(username, newHashedPassword);
    }
}

