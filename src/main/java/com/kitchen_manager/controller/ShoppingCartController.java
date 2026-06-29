package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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


    /**
     * 更新购物车中食材的购买状态
     * POST /api/cart/update-status
     */
    @PostMapping("/update-status")
    public ApiResponse<Void> updateIngredientStatus(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId,
            @RequestParam("ingredient_id") Integer ingredientId,
            @RequestParam("status") String status) {

        try {
            System.out.println("=== 更新购物车状态请求 ===");
            System.out.println("userId: " + userId);
            System.out.println("recipeId: " + recipeId);
            System.out.println("ingredientId: " + ingredientId);
            System.out.println("status: " + status);
            System.out.println("=== ===");

            // 简单验证，允许大小写
            String statusLower = status == null ? "" : status.trim().toLowerCase();
            if (!"pending".equals(statusLower) && !"purchased".equals(statusLower)) {
                System.out.println("状态值无效: " + status);
                return ApiResponse.error("状态值无效，必须是 'pending' 或 'purchased'");
            }

            recipeService.updateCartIngredientStatus(userId, recipeId, ingredientId, statusLower);
            System.out.println("更新成功");
            return ApiResponse.success("状态更新成功", null);
        } catch (Exception e) {
            System.err.println("更新状态失败: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.error("更新状态失败: " + e.getMessage());
        }
    }

    /**
     * 批量更新菜谱所有食材状态
     * POST /api/cart/update-all-status
     */
    @PostMapping("/update-all-status")
    public ApiResponse<Void> updateAllIngredientsStatus(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId,
            @RequestParam("status") String status) {

        try {
            String statusLower = status == null ? "" : status.trim().toLowerCase();
            if (!"pending".equals(statusLower) && !"purchased".equals(statusLower)) {
                return ApiResponse.error("状态值无效，必须是 'pending' 或 'purchased'");
            }

            recipeService.updateAllIngredientsStatus(userId, recipeId, statusLower);
            return ApiResponse.success("批量更新成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("批量更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除购物车中的食材
     * POST /api/cart/delete-ingredient
     */
    @PostMapping("/delete-ingredient")
    public ApiResponse<Void> deleteIngredientFromCart(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId,
            @RequestParam("ingredient_id") Integer ingredientId) {

        try {
            recipeService.deleteCartIngredient(userId, recipeId, ingredientId);
            return ApiResponse.success("食材删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("删除食材失败: " + e.getMessage());
        }
    }

    /**
     * 删除购物车中的整个菜谱
     * POST /api/cart/delete-recipe
     */
    @PostMapping("/delete-recipe")
    public ApiResponse<Void> deleteRecipeFromCart(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            recipeService.removeFromShoppingCart(userId, recipeId);
            return ApiResponse.success("菜谱删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("删除菜谱失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户购物车中的菜谱（按菜谱分组）
     * GET /api/cart/shopping-recipes
     */
    @GetMapping("/shopping-recipes")
    public ApiResponse<List<Map<String, Object>>> getShoppingCartRecipes(
            @RequestParam("user_id") Integer userId) {

        try {
            List<Map<String, Object>> recipes = recipeService.getGroupedShoppingCartByUserId(userId);
            return ApiResponse.success(recipes);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取购物车菜谱失败: " + e.getMessage());
        }
    }
}
