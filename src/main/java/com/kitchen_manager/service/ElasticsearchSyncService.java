package com.kitchen_manager.service;

import com.kitchen_manager.elasticsearch.RecipeDocument;
import com.kitchen_manager.elasticsearch.RecipeElasticsearchRepository;
import com.kitchen_manager.entity.Recipe;
import com.kitchen_manager.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ElasticsearchSyncService {

    private final RecipeRepository recipeRepository;
    private final RecipeElasticsearchRepository esRepository;

    /**
     * 全量同步：将 MySQL 中的所有菜谱同步到 Elasticsearch
     */
    @Transactional(readOnly = true)
    public void syncAll() {
        System.out.println("开始全量同步菜谱到 Elasticsearch...");

        List<Recipe> recipes = recipeRepository.findAll();

        List<RecipeDocument> documents = recipes.stream()
                .map(this::convertToDocument)
                .collect(Collectors.toList());

        esRepository.saveAll(documents);

        System.out.println("同步完成！共同步 " + documents.size() + " 个菜谱");
    }

    /**
     * 单个菜谱同步
     */
    public void syncOne(Integer recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RuntimeException("菜谱不存在"));

        RecipeDocument document = convertToDocument(recipe);
        esRepository.save(document);
    }

    /**
     * 删除索引中的菜谱
     */
    public void deleteOne(Integer recipeId) {
        esRepository.deleteById(recipeId);
    }

    /**
     * 转换为 Elasticsearch 文档
     * 使用 needs 字段（JSON）而不是查询 ingredient 表
     */
    private RecipeDocument convertToDocument(Recipe recipe) {
        RecipeDocument doc = new RecipeDocument();
        doc.setRecipeId(recipe.getRecipeId());
        doc.setName(recipe.getName());
        doc.setTaste(recipe.getTaste());
        doc.setMethod(recipe.getMethod());
        doc.setDifficulty(recipe.getDifficulty());
        doc.setTime(recipe.getTime());
        doc.setPopularity(recipe.getPopularity());
        doc.setImageUrl(recipe.getImageUrl());

        // ✓ 直接使用 needs 字段（JSON 数组）
        List<String> ingredientList = recipe.getNeedsAsList();

        if (!ingredientList.isEmpty()) {
            // 拼接成字符串，去掉可能的标点符号干扰
            String ingredientNames = ingredientList.stream()
                    .map(name -> name.trim().replaceAll("[。,，、]$", ""))  // 去除末尾标点
                    .collect(Collectors.joining(", "));
            doc.setIngredients(ingredientNames);
        } else {
            doc.setIngredients("");
        }

        return doc;
    }
}