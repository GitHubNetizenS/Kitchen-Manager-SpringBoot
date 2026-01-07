package com.kitchen_manager.dto;

import lombok.Data;
import java.util.Map;

@Data
public class RecipeWithFavoriteDTO {
    private Integer recipeId;
    private String name;
    private String imageUrl;
    private String taste;
    private String method;
    private String time;
    private String difficulty;
    private String needs;
    private Integer popularity;
    private Boolean isFavorite;

    public static RecipeWithFavoriteDTO fromMap(Map<String, Object> map) {
        RecipeWithFavoriteDTO dto = new RecipeWithFavoriteDTO();
        dto.setRecipeId((Integer) map.get("recipe_id"));
        dto.setName((String) map.get("name"));
        dto.setImageUrl((String) map.get("image_url"));
        dto.setTaste((String) map.get("taste"));
        dto.setMethod((String) map.get("method"));
        dto.setTime((String) map.get("time"));
        dto.setDifficulty((String) map.get("difficulty"));
        dto.setNeeds((String) map.get("needs"));
        dto.setPopularity(map.get("popularity") != null ? ((Number) map.get("popularity")).intValue() : 0);

        // 处理 isFavorite 字段
        Object favoriteObj = map.get("isFavorite");
        if (favoriteObj instanceof Boolean) {
            dto.setIsFavorite((Boolean) favoriteObj);
        } else if (favoriteObj instanceof Number) {
            dto.setIsFavorite(((Number) favoriteObj).intValue() == 1);
        } else {
            dto.setIsFavorite(false);
        }

        return dto;
    }
}