package com.kitchen_manager.entity;

import lombok.Data;
import jakarta.persistence.*;

/**
 * 用户表
 */
@Data
@Entity
@Table(name = "User")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;                 // 用户唯一标识

    @Column(name = "username")
    private String username;                // 用户名

    @Column(name = "password")
    private String password;                // 密码

    @Column(name = "phone")
    private String phone;                   // 手机号

    @Column(name = "avatar_url")
    private String avatarUrl;               // 头像URL

    @Column(name = "is_admin")
    private Boolean isAdmin = false;        // 是否为管理员的标识（默认为否。）

    @Column(name = "title")
    private String title = "美食爱好者";     // 头衔（默认为“美食爱好者”。）
}