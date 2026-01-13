package com.kitchen_manager.service;

import com.kitchen_manager.dto.UserFeatureContext;
import com.kitchen_manager.entity.Recipe;
import com.kitchen_manager.entity.RecipeTag;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ScoreCalculationService {

    /**
     * 计算标签匹配度
     */
    public double calculateTagMatchScore(Recipe recipe, UserFeatureContext ctx) {
        if (recipe == null || ctx == null) {
            return 0.0;
        }

        // 从 RecipeService 中复制过来的代码
        Set<Integer> userTagIds = ctx.getUserTagSet();
        List<RecipeTag> recipeTags = recipe.getRecipeTags();

        if (userTagIds == null || userTagIds.isEmpty()
                || recipeTags == null || recipeTags.isEmpty()) {
            return 0.0;
        }

        double[] userVec = new double[13];
        double[] recipeVec = new double[13];

        // 构造用户向量（0/1）
        for (Integer tagId : userTagIds) {
            int idx = tagId - 1;
            if (idx >= 0 && idx < 13) {
                userVec[idx] = 1.0;
            }
        }

        // 构造菜谱向量（带权）
        for (RecipeTag rt : recipeTags) {
            int idx = rt.getTagId() - 1;
            if (idx >= 0 && idx < 13) {
                recipeVec[idx] = rt.getMatchAmount();
            }
        }

        double dot = 0.0;
        double normUser = 0.0;
        double normRecipe = 0.0;

        for (int i = 0; i < 13; i++) {
            dot += userVec[i] * recipeVec[i];
            normUser += userVec[i] * userVec[i];
            normRecipe += recipeVec[i] * recipeVec[i];
        }

        if (normUser == 0.0 || normRecipe == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normUser) * Math.sqrt(normRecipe));
    }

    /**
     * 计算原料匹配度
     */
    public double calculateIngredientMatchScore(Recipe recipe, UserFeatureContext ctx) {
        // 从 RecipeService 中复制 calculateIngredientMatchScore 方法
        if (null == recipe || null == ctx) {
            return -1.0;
        }

        Set<Integer> userIngredientIds = ctx.getUserIngredientSet();
        Map<Integer, Double> idfMap = ctx.getIngredientIdfMap();
        List<Integer> recipeIngredientIds = recipe.getIngredientIds();

        if (null == recipeIngredientIds || recipeIngredientIds.isEmpty()) {
            return -1.0;
        }
        if (userIngredientIds == null || userIngredientIds.isEmpty()) {
            return 0.0;
        }

        int matchCount = 0;

        for (Integer ing : recipeIngredientIds) {
            if (userIngredientIds.contains(ing)) {
                ++matchCount;
            }
        }

        double coverage = (double) matchCount / recipeIngredientIds.size();

        if (coverage < 0.15) {
            return coverage * 0.01;
        }

        double tf = 1.0 / recipeIngredientIds.size();
        double missingIdfSum = 0.0;
        double totalIdfSum = 0.0;

        for (Integer ing : recipeIngredientIds) {
            double idf = idfMap.getOrDefault(ing, 1.0);
            double weight = idf * tf;

            totalIdfSum += weight;
            if (!userIngredientIds.contains(ing)) {
                missingIdfSum += weight;
            }
        }
        double missingCoreRatio = totalIdfSum > 0.0 ? missingIdfSum / totalIdfSum : 1.0;

        return coverage * (1.0 - missingCoreRatio);
    }
}