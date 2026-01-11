package com.kitchen_manager.dto;

import lombok.Getter;
import java.util.Map;
import java.util.Set;

/**
 * 用户的菜谱相关特征上下文类
 */
@Getter
public class UserFeatureContext {
    private final Set<Integer>          userTagSet;
    private final Set<Integer>          userIngredientSet;
    private final Map<Integer, Double>  ingredientIdfMap;

    public UserFeatureContext(Set<Integer> userTagSet,
                              Set<Integer> userIngredientSet,
                              Map<Integer, Double> ingredientIdfMap) {
        this.userTagSet = userTagSet;
        this.userIngredientSet = userIngredientSet;
        this.ingredientIdfMap = ingredientIdfMap;
    }
}