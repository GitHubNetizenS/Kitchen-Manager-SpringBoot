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

    @Enumerated(EnumType.STRING)
    private ShoppingStatus status = ShoppingStatus.PENDING;

    @Column(name = "added_time")
    private Timestamp addedTime;

    public enum ShoppingStatus {
        PENDING, PURCHASED
    }
}