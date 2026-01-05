package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.entity.*;
import com.kitchen_manager.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FavoriteController {
    private final RecipeService recipeService;

    /**
     * 收藏菜谱
     * POST /api/favoriterecipe
     */
    @PostMapping("/favoriterecipe")
    public ApiResponse<Void> favoriteRecipe(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            recipeService.addFavorite(userId, recipeId);
            return ApiResponse.success("收藏成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(409, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("收藏失败: " + e.getMessage());
        }
    }

    /**
     * 取消收藏
     * POST /api/unfavorite
     */
    @PostMapping("/unfavorite")
    public ApiResponse<Void> unfavoriteRecipe(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            recipeService.removeFavorite(userId, recipeId);
            return ApiResponse.success("取消收藏成功", null);
        } catch (Exception e) {
            return ApiResponse.error("取消收藏失败: " + e.getMessage());
        }
    }

    /**
     * 获取收藏数量
     * GET /api/favorites/count
     */
    @GetMapping("/favorites/count")
    public ApiResponse<Long> getFavoriteCount(@RequestParam("user_id") Integer userId) {
        try {
            long count = recipeService.getFavoriteCount(userId);
            return ApiResponse.success(count);
        } catch (Exception e) {
            return ApiResponse.error("获取收藏数量失败: " + e.getMessage());
        }
    }

    /**
     * 获取收藏列表
     * GET /api/favorites
     */
    @GetMapping("/favorites")
    public ApiResponse<List<Recipe>> getFavorites(
            @RequestParam("user_id") Integer userId,
            @RequestParam(value = "sort", defaultValue = "time") String sortType) {

        try {
            // 修复：调用service获取完整的菜谱列表，而不是只获取ID
            List<Recipe> favorites = recipeService.getFavoriteRecipes(userId, sortType);
            return ApiResponse.success(favorites);
        } catch (Exception e) {
            return ApiResponse.error("获取收藏列表失败: " + e.getMessage());
        }
    }
}