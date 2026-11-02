package com.agrimax.identity.service;

import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.identity.dto.UserDto;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.agrimax.identity.service.UserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserService userService; 

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

    /**
     * 🆕 Role check helper — used by voice handlers for role enforcement.
     */
    @Transactional(readOnly = true)
    public boolean hasRole(UUID userId, String role) {
        if (userId == null || role == null) return false;
        // In handle(), right after auth check:
if (!userService.hasRole(userId, "BUYER")) {
    log.warn("Non-buyer attempted PLACE_BID: userId={}", userId);
    return CommandResult.failWithAction(
            "Only buyers can place bids. Farmers create sell posts instead.",
            "صرف خریدار bid لگا سکتے ہیں۔ کسان اپنی فصل کی پوسٹ بناتے ہیں۔",
            UIAction.navigate("/marketplace"));
}
        return userRepository.findById(userId)
                .map(u -> role.equalsIgnoreCase(u.getRole()))
                .orElse(false);
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

    import com.agrimax.identity.service.UserService;

// Add field:
private final UserService userService;

// In handle(), after auth check (`userId == null` block):
if (!userService.hasRole(userId, "FARMER")) {
    log.warn("Non-farmer attempted CREATE_SELL_POST: userId={}", userId);
    return CommandResult.failWithAction(
            "Only farmers can create sell posts.",
            "صرف کسان فصل بیچنے کی پوسٹ بنا سکتے ہیں۔",
            UIAction.navigate("/marketplace"));
}
}