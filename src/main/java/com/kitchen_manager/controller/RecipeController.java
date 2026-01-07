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

    /**
     * 获取菜谱列表
     * GET /api/recipes
     */
    @GetMapping("/recipes")
    public ApiResponse<List<Recipe>> getRecipes(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "100") int pageSize) {

        try {
            List<Recipe> recipes = recipeService.getAllRecipes();
            return ApiResponse.success(recipes);
        } catch (Exception e) {
            return ApiResponse.error("获取菜谱列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取菜谱详情
     * GET /api/recipedetail
     */
    @GetMapping("/recipedetail")
    public ApiResponse<Recipe> getRecipeDetail(
            @RequestParam("recipe_id") Integer recipeId) {

        try {
            Recipe recipe = recipeService.getRecipeDetail(recipeId);
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
     * 按标签获取菜谱列表
     * GET /api/recipelist
     */
    @GetMapping("/recipelist")
    public ApiResponse<Map<String, Object>> getRecipeListByTag(
            @RequestParam(value = "tag_id", defaultValue = "0") Integer tagId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(value = "user_id") Integer userId) {

        if(null==userId || 0==userId) {
            return ApiResponse.error("用户未登录，无法获取个性化推荐");
        }

        try {
            Map<String, Object> result = recipeService.getRecipesByTagAndPage(tagId, page, pageSize, userId);

            return ApiResponse.success(result);
        } catch (Exception e) {

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
}
