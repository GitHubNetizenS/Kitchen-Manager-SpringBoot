package com.kitchen_manager.dto;

import lombok.Data;

@Data
public class UserResponse {
    private Integer userId;
    private String username;
    private String phone;
    private String avatarUrl;
    private Boolean isAdmin;
    private String title;
}

