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
public class HistoryController {
    private final RecipeService recipeService;

    /**
     * 添加烹饪历史
     * POST /api/addhistory
     */
    @PostMapping("/addhistory")
    public ApiResponse<Void> addHistory(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            recipeService.addHistory(userId, recipeId);
            return ApiResponse.success("烹饪历史记录添加成功", null);
        } catch (Exception e) {
            return ApiResponse.error("添加历史记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取历史记录数量
     * GET /api/history/count
     */
    @GetMapping("/history/count")
    public ApiResponse<Long> getHistoryCount(@RequestParam("user_id") Integer userId) {
        try {
            long count = recipeService.getHistoryCount(userId);
            return ApiResponse.success(count);
        } catch (Exception e) {
            return ApiResponse.error("获取历史记录数量失败: " + e.getMessage());
        }
    }

    /**
     * 获取历史记录列表
     * GET /api/history?user_id=1&sort=time
     * sort参数: time(按烹饪时间) 或 match(按匹配值)
     */
    @GetMapping("/history")
    public ApiResponse<List<Recipe>> getHistory(
            @RequestParam("user_id") Integer userId,
            @RequestParam(value = "sort", defaultValue = "time") String sortType) {

        try {
            List<Recipe> recipes = recipeService.getHistoryRecipes(userId, sortType);
            return ApiResponse.success(recipes);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取历史记录失败: " + e.getMessage());
        }
    }

    /**
     * 删除烹饪历史记录
     * POST /api/deletehistory
     */
    @PostMapping("/deletehistory")
    public ApiResponse<Void> deleteHistory(
            @RequestParam("user_id") Integer userId,
            @RequestParam("recipe_id") Integer recipeId) {
        try {
            recipeService.deleteHistory(userId, recipeId);
            return ApiResponse.success("删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }
}