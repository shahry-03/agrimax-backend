package com.agrimax.identity.service;

import com.agrimax.identity.entity.User;

import java.util.List;

public interface BackupCodeService {
    List<String> generateCodes(User user, int count);
    boolean verifyAndConsume(User user, String code);
    long countRemaining(User user);
    void invalidateAll(User user);
}