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
}