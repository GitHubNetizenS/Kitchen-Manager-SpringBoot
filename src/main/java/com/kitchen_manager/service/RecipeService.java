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

    /**
     * 获取用户收藏的菜谱列表（支持按时间或匹配值排序）
     */
    public List<Recipe> getFavoriteRecipes(Integer userId, String sortType) {
        if ("time".equals(sortType)) {
            // 按收藏时间排序
            return getFavoriteRecipesByTime(userId);
        } else if ("match".equals(sortType)) {
            // 按匹配值排序
            return getFavoriteRecipesByMatch(userId);
        } else {
            // 默认按时间排序
            return getFavoriteRecipesByTime(userId);
        }
    }

    /**
     * 按收藏时间排序获取菜谱
     */
    private List<Recipe> getFavoriteRecipesByTime(Integer userId) {
        // 获取按时间排序的收藏记录
        List<UserFavoriteRecipe> favorites = favoriteRepository
                .findByUserIdOrderByFavoriteTimeDesc(userId);

        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }

        // 提取菜谱ID（保持时间顺序）
        List<Integer> recipeIds = favorites.stream()
                .map(UserFavoriteRecipe::getRecipeId)
                .collect(Collectors.toList());

        // 获取菜谱详情
        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);

        // 按原始顺序重新排列（因为findAllById不保证顺序）
        Map<Integer, Recipe> recipeMap = recipes.stream()
                .collect(Collectors.toMap(Recipe::getRecipeId, r -> r));

        return recipeIds.stream()
                .map(recipeMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 按匹配值排序获取菜谱
     */
    private List<Recipe> getFavoriteRecipesByMatch(Integer userId) {
        // 获取收藏的菜谱ID
        List<Integer> recipeIds = favoriteRepository.findRecipeIdsByUserId(userId);

        if (recipeIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 使用自定义查询按匹配值排序
        return recipeRepository.findByRecipeIdsOrderByMatchValue(recipeIds, userId);
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

    /**
     * 获取用户烹饪历史菜谱列表（支持按时间或匹配值排序）
     */
    public List<Recipe> getHistoryRecipes(Integer userId, String sortType) {
        if ("time".equals(sortType)) {
            return getHistoryRecipesByTime(userId);
        } else if ("match".equals(sortType)) {
            return getHistoryRecipesByMatch(userId);
        } else {
            return getHistoryRecipesByTime(userId);
        }
    }

    /**
     * 按烹饪时间排序获取历史菜谱
     */
    private List<Recipe> getHistoryRecipesByTime(Integer userId) {
        List<UserHistory> historyList = historyRepository
                .findByUserIdOrderByCookTimeDesc(userId);

        if (historyList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> recipeIds = historyList.stream()
                .map(UserHistory::getRecipeId)
                .collect(Collectors.toList());

        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);

        Map<Integer, Recipe> recipeMap = recipes.stream()
                .collect(Collectors.toMap(Recipe::getRecipeId, r -> r));

        return recipeIds.stream()
                .map(recipeMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 按匹配值排序获取历史菜谱
     */
    private List<Recipe> getHistoryRecipesByMatch(Integer userId) {
        List<Integer> recipeIds = historyRepository.findRecipeIdsByUserId(userId);

        if (recipeIds.isEmpty()) {
            return new ArrayList<>();
        }

        return recipeRepository.findByRecipeIdsOrderByMatchValue(recipeIds, userId);
    }
}