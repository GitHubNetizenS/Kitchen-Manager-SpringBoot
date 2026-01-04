package com.kitchen_manager.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private Integer userId;
    private String username;
    private String phone;
    private String title;
    private String avatarUrl;
}

