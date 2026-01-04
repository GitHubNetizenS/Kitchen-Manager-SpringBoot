package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Entity
@Table(name = "RecipeIngredient")
public class RecipeIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "recipe_id")
    private Integer recipeId;

    @Column(name = "ingredient_id")
    private Integer ingredientId;
}
