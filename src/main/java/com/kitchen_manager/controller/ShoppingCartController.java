package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class ShoppingCartController {

    private final RecipeService recipeService;

    /**
     * 切换菜谱的购物车状态（添加/移除）
     * POST /api/cart/toggle
     */
    @PostMapping("/toggle")
    public ApiResponse<Void> toggleCart(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            // 调用新的toggle方法
            recipeService.toggleShoppingCart(userId, recipeId);

            // 检查当前状态
            boolean isInCart = recipeService.isRecipeInCart(userId, recipeId);
            String message = isInCart ? "已加入购物车" : "已从购物车移除";

            return ApiResponse.success(message, null);
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }


    /**
     * 将菜谱加入购物车
     * POST /api/cart/add
     */
    @PostMapping("/add")
    public ApiResponse<Void> addToCart(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            recipeService.addToShoppingCart(userId, recipeId);
            return ApiResponse.success("加入购物车成功", null);
        } catch (Exception e) {
            return ApiResponse.error("加入购物车失败: " + e.getMessage());
        }
    }

    /**
     * 从购物车移除菜谱
     * POST /api/cart/remove
     */
    @PostMapping("/remove")
    public ApiResponse<Void> removeFromCart(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            recipeService.removeFromShoppingCart(userId, recipeId);
            return ApiResponse.success("从购物车移除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 获取购物车中的菜谱
     * GET /api/cart/recipes
     */
    @GetMapping("/recipes")
    public ApiResponse<List<Integer>> getCartRecipes(
            @RequestParam("user_id") Integer userId) {

        try {
            List<Integer> recipeIds = recipeService.getShoppingCartRecipeIds(userId);
            return ApiResponse.success(recipeIds);
        } catch (Exception e) {
            return ApiResponse.error("获取购物车失败: " + e.getMessage());
        }
    }

    /**
     * 检查菜谱是否在购物车中
     * GET /api/cart/check
     */
    @GetMapping("/check")
    public ApiResponse<Boolean> checkIfInCart(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            boolean isInCart = recipeService.isRecipeInCart(userId, recipeId);
            return ApiResponse.success(isInCart);
        } catch (Exception e) {
            return ApiResponse.error("检查失败: " + e.getMessage());
        }
    }
}