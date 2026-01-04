package com.kitchen_manager.entity;

import lombok.Data;
import jakarta.persistence.*;

/**
 * 用户偏好与记录表
 */
@Data
@Entity
@Table(name = "UserTag")
public class UserTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;     // 用户偏好记录ID

    @Column(name = "user_id")
    private Integer userId; // 关联用户表

    @Column(name = "tag_id")
    private Integer tagId;  // 关联标签表
}