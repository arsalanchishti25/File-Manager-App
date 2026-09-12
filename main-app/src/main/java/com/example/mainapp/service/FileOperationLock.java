package com.example.mainapp.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public final class FileOperationLock {

    private static final ConcurrentMap<Long, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private FileOperationLock() {
    }

    public static ReentrantLock forFile(long fileId) {
        if (fileId <= 0) {
            throw new IllegalArgumentException("File ID must be positive");
        }
        return LOCKS.computeIfAbsent(fileId, ignored -> new ReentrantLock());
    }
}
