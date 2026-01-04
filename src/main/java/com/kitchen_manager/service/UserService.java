package com.kitchen_manager.service;

import com.kitchen_manager.entity.*;
import com.kitchen_manager.dto.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User register(String username, String password, String phone) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已被使用");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new RuntimeException("该手机号已注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setPhone(phone);
        user.setTitle("美食爱好者");

        return userRepository.save(user);
    }

    public User login(String loginId, String password) {
        return userRepository.authenticate(loginId, password)
                .orElseThrow(() -> new RuntimeException("用户名/手机号或密码错误"));
    }

    @Transactional
    public User updateProfile(UserUpdateRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setUsername(request.getUsername());
        user.setPhone(request.getPhone());
        if (request.getTitle() != null) {
            user.setTitle(request.getTitle());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        return userRepository.save(user);
    }

    public User getUserById(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setPhone(user.getPhone());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setIsAdmin(user.getIsAdmin());
        response.setTitle(user.getTitle());
        return response;
    }
}