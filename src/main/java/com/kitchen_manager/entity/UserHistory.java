package com.kitchen_manager.entity;

import lombok.Data;
import java.sql.Timestamp;
import jakarta.persistence.*;

/**
 * 用户历史记录表
 */
@Data
@Entity
@Table(name = "user_history")
public class UserHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;         // 用户历史记录关联ID

    @Column(name = "user_id")
    private Integer userId;     // 关联用户ID

    @Column(name = "recipe_id")
    private Integer recipeId;   // 关联菜谱ID

    @Column(name = "cook_time")
    private Timestamp cookTime; // 烹饪时间（该时间指点击“吃这个”的时间。）
}