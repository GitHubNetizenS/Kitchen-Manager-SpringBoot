package com.kitchen_manager.service;

import com.kitchen_manager.common.LightGBMRankPredictor;
import com.kitchen_manager.elasticsearch.RecipeDocument;
import com.kitchen_manager.entity.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final ElasticsearchOperations elasticsearchOperations;
    /**
     * 搜索菜谱并排序
     * @param keyword 搜索关键词
     * @param sortType 排序类型：all(综合), tag_match(标签匹配), ingredient_match(原料匹配)
     * @param userId 用户ID
     * @return 排序后的菜谱列表
     */
    /**
     * 使用 Elasticsearch 搜索菜谱
     */
    public List<Recipe> search(String keyword, String sortType, Integer userId) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Step 1: 使用 Elasticsearch 搜索（基于 needs）
        List<Integer> recipeIds = searchWithElasticsearch(keyword.trim());

        if (recipeIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Step 2: 从 MySQL 获取完整的菜谱信息
        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);

        // Step 3: 根据排序类型进行排序（基于 ingredient）
        if ("tag_match".equals(sortType)) {
            return sortByTagMatchOnly(recipes, userId);
        } else if ("ingredient_match".equals(sortType)) {
            return sortByIngredientMatchOnly(recipes, userId);
        } else {
            return sortByLightGBM(recipes, userId);
        }
    }

    /**
     * Elasticsearch 搜索实现
     */
    private List<Integer> searchWithElasticsearch(String keyword) {
        try {
            // ✓ 搜索 name 和 needs
            Query searchQuery = NativeQuery.builder()
                    .withQuery(q -> q
                            .multiMatch(m -> m
                                    .query(keyword)
                                    .fields("name", "ingredients")  // ingredients 来自 needs
                                    .fuzziness("AUTO")
                            )
                    )
                    .withPageable(PageRequest.of(0, 100))
                    .build();

            SearchHits<RecipeDocument> searchHits =
                    elasticsearchOperations.search(searchQuery, RecipeDocument.class);

            return searchHits.stream()
                    .map(SearchHit::getContent)
                    .map(RecipeDocument::getRecipeId)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Elasticsearch 搜索失败: " + e.getMessage());
            e.printStackTrace();

            // 降级：使用 MySQL 搜索
            return recipeRepository.searchByKeyword(keyword).stream()
                    .map(Recipe::getRecipeId)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 综合排序：使用 LightGBM 模型（复用首页推荐算法）
     */
    private List<Recipe> sortByLightGBM(List<Recipe> recipes, Integer userId) {
        if (userId == null || userId <= 0) {
            // 未登录用户，按热度排序
            recipes.sort((r1, r2) -> Integer.compare(r2.getPopularity(), r1.getPopularity()));
            return recipes;
        }

        // 计算三个特征
        List<List<Double>> featureList = new ArrayList<>();

        for (Recipe recipe : recipes) {
            // ① 标签匹配度
            double tagScore = recipeService.calculateTagMatchScore(recipe, userId);
            recipe.setTagMatchScore(tagScore);

            // ② 原料匹配度
            double ingredientScore = recipeService.calculateIngredientMatchScore(recipe, userId);
            recipe.setIngredientMatchScore(ingredientScore);

            // ③ 热度特征
            double hotScore = Math.log(1 + recipe.getPopularity());
            recipe.setHotScore(hotScore);

            featureList.add(Arrays.asList(tagScore, ingredientScore, hotScore));
        }

        // 调用 Python 模型预测
        LightGBMRankPredictor predictor = new LightGBMRankPredictor(
                "D:\\python3.12\\python.exe",
                "python\\rank_predictor.py",
                "python\\lightgbm_rank_model.txt"
        );

        List<Double> scores;
        try {
            scores = predictor.predictScores(featureList);
        } catch (Exception e) {
            System.err.println("LightGBM模型预测失败，降级到简单加权: " + e.getMessage());
            // 降级策略：简单加权
            scores = new ArrayList<>();
            for (Recipe recipe : recipes) {
                double score = recipe.getTagMatchScore() * 0.4
                        + recipe.getIngredientMatchScore() * 0.4
                        + recipe.getHotScore() * 0.2;
                scores.add(score);
            }
        }

        // 设置预测分数并排序
        for (int i = 0; i < recipes.size(); i++) {
            recipes.get(i).setPredictScore(scores.get(i));
        }

        recipes.sort((r1, r2) -> Double.compare(r2.getPredictScore(), r1.getPredictScore()));

        return recipes;
    }

    /**
     * 只按标签匹配度排序
     */
    private List<Recipe> sortByTagMatchOnly(List<Recipe> recipes, Integer userId) {
        if (userId == null || userId <= 0) {
            // 未登录用户，按热度排序
            recipes.sort((r1, r2) -> Integer.compare(r2.getPopularity(), r1.getPopularity()));
            return recipes;
        }

        // 计算标签匹配度
        for (Recipe recipe : recipes) {
            double tagScore = recipeService.calculateTagMatchScore(recipe, userId);
            recipe.setTagMatchScore(tagScore);
        }

        // 按标签匹配度降序排序
        recipes.sort((r1, r2) -> Double.compare(r2.getTagMatchScore(), r1.getTagMatchScore()));

        return recipes;
    }

    /**
     * 只按原料匹配度排序
     */
    private List<Recipe> sortByIngredientMatchOnly(List<Recipe> recipes, Integer userId) {
        if (userId == null || userId <= 0) {
            // 未登录用户，按热度排序
            recipes.sort((r1, r2) -> Integer.compare(r2.getPopularity(), r1.getPopularity()));
            return recipes;
        }

        // 计算原料匹配度
        for (Recipe recipe : recipes) {
            double ingredientScore = recipeService.calculateIngredientMatchScore(recipe, userId);
            recipe.setIngredientMatchScore(ingredientScore);
        }

        // 按原料匹配度降序排序
        recipes.sort((r1, r2) -> Double.compare(r2.getIngredientMatchScore(), r1.getIngredientMatchScore()));

        return recipes;
    }
}