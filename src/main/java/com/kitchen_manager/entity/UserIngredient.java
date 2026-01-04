package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Entity
@Table(name = "user_ingredient")
public class UserIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer stockId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "ingredient_id")
    private Integer ingredientId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "storage_time")
    private Timestamp storageTime;
}
