package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.dto.*;
import com.kitchen_manager.service.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 食材相关接口 (扩展)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserIngredientController {
    private final UserIngredientService userIngredientService;
    private final IngredientService ingredientService;
    private final Gson gson = new Gson();

    /**
     * 获取用户食材列表
     * POST /api/user/ingredients
     */
    @PostMapping("/user/ingredients")
    public ApiResponse<List<IngredientDetailResponse>> getUserIngredients(
            @RequestParam("user_id") Integer userId) {

        try {
            List<IngredientDetailResponse> ingredients =
                    userIngredientService.getUserIngredients(userId);
            return ApiResponse.success("获取食材成功", ingredients);
        } catch (Exception e) {
            return ApiResponse.error("数据库错误: " + e.getMessage());
        }
    }

    /**
     * 添加食材
     * POST /api/addingredient
     */
    @PostMapping("/addingredient")
    public ApiResponse<String> addIngredients(
            @RequestParam("user_id") Integer userId,
            @RequestParam("ingredients") String ingredientsJson) {

        try {
            List<String> ingredients = gson.fromJson(ingredientsJson,
                    new TypeToken<List<String>>(){}.getType());

            int matchedCount = ingredientService.addIngredients(userId, ingredients);
            return ApiResponse.success("成功添加 " + matchedCount + " 种食材", null);
        } catch (Exception e) {
            return ApiResponse.error("添加食材失败: " + e.getMessage());
        }
    }

    /**
     * 删除单个食材
     * POST /api/deleteingredient
     */
    @PostMapping("/deleteingredient")
    public ApiResponse<Void> deleteIngredient(
            @RequestParam("user_id") Integer userId,
            @RequestParam("ingredient_name") String ingredientName) {

        try {
            ingredientService.deleteUserIngredient(userId, ingredientName);
            return ApiResponse.success("删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除食材
     * POST /api/user/ingredients/delete
     */
    @PostMapping("/user/ingredients/delete")
    public ApiResponse<String> deleteUserIngredients(
            @RequestParam("user_id") Integer userId,
            @RequestParam("ingredient_ids") String ingredientIdsJson) {

        try {
            List<Integer> ingredientIds = gson.fromJson(ingredientIdsJson,
                    new TypeToken<List<Integer>>(){}.getType());

            ingredientService.deleteUserIngredients(userId, ingredientIds);
            return ApiResponse.success("成功删除 " + ingredientIds.size() + " 种食材", null);
        } catch (Exception e) {
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 更新食材信息
     * POST /api/updateingredient
     */
    @PostMapping("/updateingredient")
    public ApiResponse<Void> updateIngredient(
            @RequestParam("user_id") Integer userId,
            @RequestParam("ingredient_name") String ingredientName,
            @RequestParam("category") String category,
            @RequestParam("storage_date") String storageDate) {

        try {
            userIngredientService.updateIngredient(userId, ingredientName, category, storageDate);
            return ApiResponse.success("更新成功", null);
        } catch (Exception e) {
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
}
