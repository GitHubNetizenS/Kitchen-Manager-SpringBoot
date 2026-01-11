package com.kitchen_manager.dto;

import com.kitchen_manager.entity.Recipe;
import lombok.Data;

@Data
public class RecipeWithStatusDTO {
    private Integer recipeId;
    private String name;
    private String imageUrl;
    private String taste;
    private String method;
    private String time;
    private String difficulty;
    private String needs;
    private String steps;
    private Integer popularity;
    private Boolean isFavorite;
    private Boolean inShoppingCart;

    // 构造方法
    public RecipeWithStatusDTO() {}

    // 从实体转换
    public static RecipeWithStatusDTO fromRecipe(Recipe recipe, Boolean isFavorite, Boolean inShoppingCart) {
        RecipeWithStatusDTO dto = new RecipeWithStatusDTO();
        dto.setRecipeId(recipe.getRecipeId());
        dto.setName(recipe.getName());
        dto.setImageUrl(recipe.getImageUrl());
        dto.setTaste(recipe.getTaste());
        dto.setMethod(recipe.getMethod());
        dto.setTime(recipe.getTime());
        dto.setDifficulty(recipe.getDifficulty());
        dto.setNeeds(recipe.getNeeds());
        dto.setSteps(recipe.getSteps());
        dto.setPopularity(recipe.getPopularity());
        dto.setIsFavorite(isFavorite);
        dto.setInShoppingCart(inShoppingCart);
        return dto;
    }
}