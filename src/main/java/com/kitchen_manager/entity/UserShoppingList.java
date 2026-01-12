package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Entity
@Table(name = "user_shopping_list")
public class UserShoppingList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "recipe_id")
    private Integer recipeId;

    @Column(name = "ingredient_id")
    private Integer ingredientId;

    @Column(name = "status")
    private String status = "pending";

    @Column(name = "added_time")
    private Timestamp addedTime;

    public enum ShoppingStatus {
        PENDING, PURCHASED
    }
}