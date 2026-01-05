package com.kitchen_manager.entity;

import lombok.Data;
import java.sql.Timestamp;
import jakarta.persistence.*;

/**
 * 用户收藏菜谱表
 */
@Data
@Entity
@Table(name = "userfavoriterecipe")
public class UserFavoriteRecipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;             // 用户收藏菜谱关联ID

    @Column(name = "user_id")
    private Integer userId;         // 关联用户ID

    @Column(name = "recipe_id")
    private Integer recipeId;       // 关联菜谱ID

    @Column(name = "favorite_time")
    private Timestamp favoriteTime; // 收藏时间
}
