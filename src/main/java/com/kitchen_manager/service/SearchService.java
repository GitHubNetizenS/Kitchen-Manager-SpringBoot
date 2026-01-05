package com.kitchen_manager.service;

import com.kitchen_manager.entity.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final RecipeRepository recipeRepository;
    private final UserTagRepository userTagRepository;
    private final UserIngredientRepository userIngredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    public List<Recipe> search(String keyword, String sortType, Integer userId) {
        // 获取匹配的菜谱
        List<Recipe> recipes = recipeRepository.searchByKeyword(keyword);

        if ("tag_match".equals(sortType)) {
            return sortByTagMatch(recipes, userId);
        } else if ("ingredient_match".equals(sortType)) {
            return sortByIngredientMatch(recipes, userId);
        } else {
            return recipes;
        }
    }

    private List<Recipe> sortByTagMatch(List<Recipe> recipes, Integer userId) {
        List<Integer> userTagIds = userTagRepository.findTagIdsByUserId(userId);

        System.out.println("userTagIds: " + userTagIds);
        // TODO: 这里需要实现标签匹配度计算逻辑
        return recipes; // 简化实现
    }

    private List<Recipe> sortByIngredientMatch(List<Recipe> recipes, Integer userId) {
        List<UserIngredient> userIngredients = userIngredientRepository.findByUserId(userId);
        List<Integer> userIngredientIds = userIngredients.stream()
                .map(UserIngredient::getIngredientId)
                .toList();

        // 计算每个菜谱的食材匹配度
        Map<Integer, Long> matchCounts = new HashMap<>();
        for (Recipe recipe : recipes) {
            List<Integer> recipeIngredientIds = recipeIngredientRepository
                    .findIngredientIdsByRecipeId(recipe.getRecipeId());

            long matchCount = recipeIngredientIds.stream()
                    .filter(userIngredientIds::contains)
                    .count();

            matchCounts.put(recipe.getRecipeId(), matchCount);
        }

        // 按匹配度排序
        recipes.sort((r1, r2) -> {
            long count1 = matchCounts.getOrDefault(r1.getRecipeId(), 0L);
            long count2 = matchCounts.getOrDefault(r2.getRecipeId(), 0L);
            return Long.compare(count2, count1);
        });

        return recipes;
    }
}