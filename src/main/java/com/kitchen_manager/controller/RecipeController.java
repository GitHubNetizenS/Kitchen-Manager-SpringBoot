package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.entity.*;
import com.kitchen_manager.repository.*;
import com.kitchen_manager.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final SearchService searchService;
    private final IngredientRepository ingredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    // 在类中添加新方法
    @GetMapping("/recipes/filter")
    public ApiResponse<Map<String, Object>> getFilteredRecipes(
            @RequestParam(value = "taste", required = false, defaultValue = "") String taste,
            @RequestParam(value = "method", required = false, defaultValue = "") String method,
            @RequestParam(value = "difficulty", required = false, defaultValue = "") String difficulty,
            @RequestParam(value = "sort", defaultValue = "all") String sort,
            @RequestParam(value = "user_id", required = false) Integer userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {

        try {
            if (userId == null) {
                userId = 0;
            }
            Map<String, Object> result = recipeService.getFilteredRecipesWithFavoriteStatus(
                    taste, method, difficulty, sort, userId, page, pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取过滤菜谱失败: " + e.getMessage());
        }
    }

    /**
     * 获取菜谱相关视频
     * GET /api/recipe/videos
     */
    @GetMapping("/recipe/videos")
    public ApiResponse<List<RecipeVideo>> getRecipeVideos(
            @RequestParam("recipe_id") Integer recipeId) {
        try {
            List<RecipeVideo> videos = recipeService.getVideosByRecipeId(recipeId);
            return ApiResponse.success(videos);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取视频失败: " + e.getMessage());
        }
    }

    /**
     * 获取菜谱详情（包含收藏状态）
     * GET /api/recipedetail
     */
    @GetMapping("/recipedetail")
    public ApiResponse<Map<String, Object>> getRecipeDetail(
            @RequestParam("recipe_id") Integer recipeId,
            @RequestParam(value = "user_id", required = false) Integer userId) {

        try {
            if (userId == null) {
                userId = 0; // 未登录用户
            }

            Map<String, Object> recipe = recipeService.getRecipeDetailWithFavoriteStatus(recipeId, userId);
            if (recipe == null) {
                return ApiResponse.error(404, "菜谱不存在");
            }
            return ApiResponse.success(recipe);
        } catch (Exception e) {
            return ApiResponse.error("获取菜谱详情失败: " + e.getMessage());
        }
    }

    /**
     * 点击增加菜谱热度
     * POST /api/recipe/popularity
     */
    @PostMapping("/recipe/popularity")
    public ApiResponse<Void> incrementPopularity(
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            recipeService.incrementPopularity(recipeId);
            return ApiResponse.success("热度增加成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("增加热度失败: " + e.getMessage());
        }
    }

    /**
     * 获取菜谱所需食材
     * GET /api/recipe/ingredients
     */
    @GetMapping("/recipe/ingredients")
    public ApiResponse<List<Ingredient>> getRecipeIngredients(
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            List<Integer> ingredientIds =
                    recipeIngredientRepository.findIngredientIdsByRecipeId(recipeId);

            List<Ingredient> ingredients =
                    ingredientRepository.findByIds(ingredientIds);

            return ApiResponse.success(ingredients);
        } catch (Exception e) {
            return ApiResponse.error("获取食材失败: " + e.getMessage());
        }
    }

    /**
     * 按标签获取菜谱列表（包含用户收藏状态）
     * GET /api/recipelist
     */
    @GetMapping("/recipelist")
    public ApiResponse<Map<String, Object>> getRecipeListByTag(
            @RequestParam(value = "tag_id", defaultValue = "0") Integer tagId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(value = "user_id", required = false) Integer userId) {

        try {
            Map<String, Object> result;

            if (userId != null && userId > 0) {
                // 登录用户获取带推荐排序和收藏状态的列表
                result = recipeService.getRecipesByTagAndPageWithRankingAndFavorite(tagId, page, pageSize, userId);
            } else {
                // 未登录用户获取普通列表（无收藏状态）
                result = recipeService.getRecipesByTagAndPageWithFavoriteStatus(tagId, page, pageSize, 0);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取菜谱列表失败: " + e.getMessage());
        }
    }

    /**
     * 搜索菜谱
     * GET /api/search
     */
    @GetMapping("/search")
    public ApiResponse<List<Recipe>> searchRecipes(
            @RequestParam("keyword") String keyword,
            @RequestParam("sort") String sortType,
            @RequestParam("user_id") Integer userId) {

        try {
            List<Recipe> recipes = searchService.search(keyword, sortType, userId);
            return ApiResponse.success("搜索成功", recipes);
        } catch (Exception e) {
            return ApiResponse.error("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 按匹配值获取菜谱
     * GET /api/recipes/match
     */
    @GetMapping("/recipes/match")
    public ApiResponse<List<Recipe>> getRecipesByMatchValue(
            @RequestParam("user_id") Integer userId) {

        try {
            // TODO: 实现匹配逻辑
            List<Recipe> recipes = recipeService.getAllRecipes();
            return ApiResponse.success(recipes);
        } catch (Exception e) {
            return ApiResponse.error("获取菜谱失败: " + e.getMessage());
        }
    }

    @GetMapping("/recipelist/withcart")
    public ApiResponse<Map<String, Object>> getRecipeListWithCartStatus(
            @RequestParam("tag_id") Integer tagId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @RequestParam("user_id") Integer userId) {

        try {
            Map<String, Object> result = recipeService.getRecipesByTagAndPageWithFavoriteAndCartStatus(tagId, page, pageSize, userId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error("获取菜谱列表失败: " + e.getMessage());
        }
    }
}