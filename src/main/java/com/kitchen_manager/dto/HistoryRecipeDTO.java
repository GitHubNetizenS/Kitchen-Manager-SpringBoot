package com.kitchen_manager.dto;

import com.kitchen_manager.entity.Recipe;
import java.sql.Timestamp;

public class HistoryRecipeDTO {
    private Integer historyId;      // 历史记录ID
    private Recipe recipe;          // 菜谱信息
    private Timestamp cookTime;     // 烹饪时间

    // 构造方法
    public HistoryRecipeDTO() {}

    public HistoryRecipeDTO(Integer historyId, Recipe recipe, Timestamp cookTime) {
        this.historyId = historyId;
        this.recipe = recipe;
        this.cookTime = cookTime;
    }

    // Getter和Setter
    public Integer getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Integer historyId) {
        this.historyId = historyId;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }

    public Timestamp getCookTime() {
        return cookTime;
    }

    public void setCookTime(Timestamp cookTime) {
        this.cookTime = cookTime;
    }
}