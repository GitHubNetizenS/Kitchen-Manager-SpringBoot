package com.kitchen_manager.dto;

/**
 * 含有收藏标记的菜谱投影接口
 */
public interface RecipeWithFavoriteProjection {
    Integer getRecipeId();
    String getName();
    String getImageUrl();
    String getTaste();
    String getMethod();
    String getTime();
    String getDifficulty();
    String getNeeds();
    String getSteps();
    Integer getPopularity();
    Integer getIsFavorite();
    Integer getInShoppingCart();
}