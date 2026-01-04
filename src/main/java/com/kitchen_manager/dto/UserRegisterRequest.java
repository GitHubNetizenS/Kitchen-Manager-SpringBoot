package com.kitchen_manager.dto;

import lombok.Data;

@Data
public class UserRegisterRequest {
    private String username;
    private String password;
    private String phone;
}
