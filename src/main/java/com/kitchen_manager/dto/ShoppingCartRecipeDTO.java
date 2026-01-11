package com.kitchen_manager.dto;

import lombok.Data;

@Data
public class ShoppingCartRecipeDTO {
    private Integer recipeId;
    private String recipeName;
    private String imageUrl;
    private Integer ingredientId;
    private String ingredientName;
    private String status; // "pending" or "purchased"
}