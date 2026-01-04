package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Entity
@Table(name = "userfavoriterecipe")
public class UserFavoriteRecipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "recipe_id")
    private Integer recipeId;

    @Column(name = "favorite_time")
    private Timestamp favoriteTime;
}
