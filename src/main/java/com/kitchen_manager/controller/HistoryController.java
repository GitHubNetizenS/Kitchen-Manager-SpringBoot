package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.dto.HistoryRecipeDTO;
import com.kitchen_manager.entity.*;
import com.kitchen_manager.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 获取历史记录列表（返回包含历史记录ID的数据）
     * GET /api/history?user_id=1&sort=time
     */
    // 在HistoryController的getHistory方法中
    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> getHistory(
            @RequestParam("user_id") Integer userId,
            @RequestParam(value = "sort", defaultValue = "time") String sortType) {

        try {
            List<HistoryRecipeDTO> dtos = recipeService.getHistoryRecipes(userId, sortType);

            // 转换为前端需要的格式
            List<Map<String, Object>> result = new ArrayList<>();
            for (HistoryRecipeDTO dto : dtos) {
                Map<String, Object> item = new HashMap<>();
                item.put("history_id", dto.getHistoryId());
                item.put("recipe_id", dto.getRecipe().getRecipeId());
                item.put("name", dto.getRecipe().getName());
                item.put("image_url", dto.getRecipe().getImageUrl());
                item.put("taste", dto.getRecipe().getTaste());
                item.put("method", dto.getRecipe().getMethod());
                item.put("time", dto.getRecipe().getTime());
                item.put("difficulty", dto.getRecipe().getDifficulty());
                item.put("needs", dto.getRecipe().getNeeds());

                // 关键：确保返回完整的时间字符串
                Timestamp cookTime = dto.getCookTime();
                if (cookTime != null) {
                    item.put("cook_time", cookTime.toString());
                } else {
                    item.put("cook_time", "");
                }

                // 添加收藏状态字段
                boolean isFavorite = recipeService.checkIfRecipeIsFavorite(userId, dto.getRecipe().getRecipeId());
                item.put("isFavorite", isFavorite);  // 重要！添加这行

                result.add(item);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取历史记录失败: " + e.getMessage());
        }
    }
    /**
     * 删除烹饪历史记录（基于历史记录ID）
     * POST /api/deletehistory
     */
    @PostMapping("/deletehistory")
    public ApiResponse<Void> deleteHistory(
            @RequestParam(value = "history_id", required = false) Integer historyId,
            @RequestParam(value = "user_id", required = false) Integer userId,
            @RequestParam(value = "recipe_id", required = false) Integer recipeId) {

        // 添加日志
        System.out.println("收到删除请求: historyId=" + historyId + ", userId=" + userId + ", recipeId=" + recipeId);

        try {
            if (historyId != null) {
                // 使用history_id删除
                recipeService.deleteHistory(historyId);
                return ApiResponse.success("删除成功", null);
            } else if (userId != null && recipeId != null) {
                // 使用user_id + recipe_id删除
                recipeService.deleteHistory(userId, recipeId);
                return ApiResponse.success("删除成功", null);
            } else {
                return ApiResponse.error("缺少必要参数");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }
}