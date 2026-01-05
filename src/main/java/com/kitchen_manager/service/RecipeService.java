package com.kitchen_manager.service;

import com.kitchen_manager.entity.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeService {
    private final RecipeRepository recipeRepository;
    private final UserFavoriteRecipeRepository favoriteRepository;
    private final UserHistoryRepository historyRepository;

    public Recipe getRecipeDetail(Integer recipeId) {
        return recipeRepository.findById(recipeId).orElse(null);
    }

    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAllOrderByPopularity();
    }

    public List<Recipe> getFavoriteRecipes(Integer userId, String sortType) {
        // 获取收藏的菜谱ID列表
        List<Integer> recipeIds = favoriteRepository.findRecipeIdsByUserId(userId);

        if (recipeIds.isEmpty()) {
            return List.of();
        }

        // 根据ID获取完整的菜谱信息
        List<Recipe> recipes = recipeRepository.findByIdIn(recipeIds);

        // 根据排序类型排序
        if ("match".equals(sortType)) {
            // 按匹配值排序（需要实现匹配算法）
            // 这里可以先返回按ID排序，后续再实现匹配算法
            return recipes.stream()
                    .sorted((r1, r2) -> r2.getRecipeId().compareTo(r1.getRecipeId()))
                    .collect(Collectors.toList());
        } else {
            // 按收藏时间排序（需要从收藏表获取时间）
            // 简化：按ID倒序
            return recipes.stream()
                    .sorted((r1, r2) -> r2.getRecipeId().compareTo(r1.getRecipeId()))
                    .collect(Collectors.toList());
        }
    }

    @Transactional
    public void addFavorite(Integer userId, Integer recipeId) {
        if (favoriteRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            throw new RuntimeException("您已经收藏过这个菜谱");
        }

        UserFavoriteRecipe favorite = new UserFavoriteRecipe();
        favorite.setUserId(userId);
        favorite.setRecipeId(recipeId);
        favorite.setFavoriteTime(new Timestamp(System.currentTimeMillis()));
        favoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(Integer userId, Integer recipeId) {
        favoriteRepository.deleteByUserIdAndRecipeId(userId, recipeId);
    }

    public long getFavoriteCount(Integer userId) {
        return favoriteRepository.countByUserId(userId);
    }

    public List<Integer> getFavoriteRecipeIds(Integer userId) {
        return favoriteRepository.findRecipeIdsByUserId(userId);
    }

    @Transactional
    public void addHistory(Integer userId, Integer recipeId) {
        UserHistory history = new UserHistory();
        history.setUserId(userId);
        history.setRecipeId(recipeId);
        history.setCookTime(new Timestamp(System.currentTimeMillis()));
        historyRepository.save(history);
    }

    public long getHistoryCount(Integer userId) {
        return historyRepository.countByUserId(userId);
    }

    public List<Integer> getHistoryRecipeIds(Integer userId) {
        return historyRepository.findRecipeIdsByUserId(userId);
    }
}