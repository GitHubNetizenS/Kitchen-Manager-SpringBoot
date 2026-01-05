package com.kitchen_manager.service;

import com.kitchen_manager.entity.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.*;

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

    public Map<String, Object> getRecipesByTagAndPage(Integer tagId, int page, int pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;

        List<Recipe> recipes;
        long total;

        // 根据 tagId 进行过滤
        if (tagId == 0) {
            // 全部菜谱
            recipes = recipeRepository.findAllOrderByPopularityWithPagination(offset, pageSize);
            total = recipeRepository.countAll();
        } else {
            // 按标签查询
            recipes = recipeRepository.findByTagIdOrderByPopularityWithPagination(tagId, offset, pageSize);
            total = recipeRepository.countByTagId(tagId);
        }

        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / pageSize);

        // 返回结果和分页信息
        Map<String, Object> result = new HashMap<>();
        result.put("recipes", recipes);
        result.put("currentPage", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("totalPages", totalPages);

        return result;
    }
}