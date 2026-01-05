package com.kitchen_manager.entity; // 注意：后端包名可能不同

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "user_history")
public class UserHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "recipe_id")
    private Integer recipeId;

    @Column(name = "cook_time")
    private Timestamp cookTime;

    // Getter和Setter
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Integer recipeId) {
        this.recipeId = recipeId;
    }

    public Timestamp getCookTime() {
        return cookTime;
    }

    public void setCookTime(Timestamp cookTime) {
        this.cookTime = cookTime;
    }
}