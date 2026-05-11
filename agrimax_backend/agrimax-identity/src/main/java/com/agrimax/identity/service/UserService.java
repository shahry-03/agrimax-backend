package com.agrimax.identity.service;

import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.identity.dto.UserDto;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserDto getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public UserDto getUserByPhone(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + phoneNumber));
        return toDto(user);
    }

    @Transactional
    public UserDto updateProfile(UUID userId, String name, String languagePref) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (name != null) user.setName(name);
        if (languagePref != null) user.setLanguagePref(languagePref);
        return toDto(userRepository.save(user));
    }

    private UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getPhoneNumber(),
                user.getName(),
                user.getRole(),
                user.getLanguagePref(),
                Boolean.TRUE.equals(user.getIsKycVerified())
        );
    }
}
