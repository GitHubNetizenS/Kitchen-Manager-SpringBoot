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
     * GET /api/history
     */
    @GetMapping("/history")
    public ApiResponse<List<Recipe>> getHistory(
            @RequestParam("user_id") Integer userId,
            @RequestParam(value = "sort", defaultValue = "time") String sortType) {

        try {
            List<Integer> recipeIds = recipeService.getHistoryRecipeIds(userId);
            // 需要根据 sortType 排序并返回完整菜谱信息
            return ApiResponse.success(List.of());
        } catch (Exception e) {
            return ApiResponse.error("获取历史记录失败: " + e.getMessage());
        }
    }
}
