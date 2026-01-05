package com.kitchen_manager.entity;

import lombok.Data;
import jakarta.persistence.*;

/**
 * 菜谱食材关联表
 */
@Data
@Entity
@Table(name = "RecipeIngredient")
public class RecipeIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;             // 关联记录ID

    @Column(name = "recipe_id")
    private Integer recipeId;       // 关联菜谱表

    @Column(name = "ingredient_id")
    private Integer ingredientId;   // 关联食材表
}
