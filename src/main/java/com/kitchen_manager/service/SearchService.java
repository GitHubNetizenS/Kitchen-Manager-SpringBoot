package com.kitchen_manager.service;

import com.kitchen_manager.common.LightGBMRankHttpPredictor;
import com.kitchen_manager.dto.UserFeatureContext;
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
    private final ElasticsearchOperations elasticsearchOperations;
    private final UserTagRepository userTagRepository;
    private final UserIngredientRepository userIngredientRepository;
    private final IngredientIdfRepository ingredientIdfRepository;
    private final RecipeTagRepository recipeTagRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final UserHistoryRepository userHistoryRepository;
    // 添加 ScoreCalculationService 依赖
    private final ScoreCalculationService scoreCalculationService;

    /**
     * 为筛选结果排序（复用搜索排序逻辑）
     */
    public List<Recipe> sortFilteredRecipes(List<Recipe> recipes, String sortType, Integer userId) {
        if (recipes == null || recipes.isEmpty()) {
            return recipes;
        }

        // 复用搜索页面的排序逻辑
        switch (sortType) {
            case "tag_match":
                return sortByTagMatchOnly(recipes, userId);
            case "ingredient_match":
                return sortByIngredientMatchOnly(recipes, userId);
            case "all":
            default:
                return sortByLightGBM(recipes, userId);
        }
    }

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
            // 拆分关键词，支持空格/逗号/顿号分隔
            String[] keywords = keyword.split("[\\s,，、]+");

            Query searchQuery = NativeQuery.builder()
                    .withQuery(q -> q
                            .bool(b -> {
                                for (String kw : keywords) {
                                    if (!kw.isBlank()) {
                                        b.should(s -> s
                                                .multiMatch(m -> m
                                                        .query(kw.trim())
                                                        .fields("name", "ingredients")
                                                        .fuzziness("AUTO")
                                                )
                                        );
                                    }
                                }
                                b.minimumShouldMatch("1");  // 至少匹配一个词
                                return b;
                            })
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
     * 综合排序：使用 LightGBM HTTP 服务（复用新版特征计算与候选集逻辑）
     */
    private List<Recipe> sortByLightGBM(List<Recipe> recipes, Integer userId) {
        UserFeatureContext ctx = buildUserFeatureContext(userId);

        if (recipes == null || recipes.isEmpty() || userId == null) {
            return recipes;
        }

        // 1. 预加载标签与原料，避免 N+1 查询
        Map<Integer, List<RecipeTag>> recipeTagMap =
                recipeTagRepository.findByRecipeIdIn(
                        recipes.stream().map(Recipe::getRecipeId).toList()
                ).stream().collect(Collectors.groupingBy(RecipeTag::getRecipeId));

        Map<Integer, List<Integer>> recipeIngredientMap =
                recipeIngredientRepository.findByRecipeIdIn(
                        recipes.stream().map(Recipe::getRecipeId).toList()
                ).stream().collect(Collectors.groupingBy(
                        RecipeIngredient::getRecipeId,
                        Collectors.mapping(RecipeIngredient::getIngredientId, Collectors.toList())
                ));

        for (Recipe recipe : recipes) {
            recipe.setRecipeTags(recipeTagMap.getOrDefault(recipe.getRecipeId(), List.of()));
            recipe.setIngredientIds(recipeIngredientMap.getOrDefault(recipe.getRecipeId(), List.of()));
        }

        // 2. 计算特征
        for (Recipe recipe : recipes) {
            double tagScore = scoreCalculationService.calculateTagMatchScore(recipe, ctx);
            double ingredientScore = scoreCalculationService.calculateIngredientMatchScore(recipe, ctx);
            double hotScore = Math.log(1 + recipe.getPopularity()) / 10.0;

            recipe.setTagMatchScore(tagScore);
            recipe.setIngredientMatchScore(ingredientScore);
            recipe.setHotScore(hotScore);
        }

        // 3. 构造候选集（与代码段 C 保持一致）
        List<Recipe> ingredientPositive = recipes.stream()
                .filter(r -> r.getIngredientMatchScore() > 0.0)
                .sorted(Comparator.comparingDouble(Recipe::getIngredientMatchScore).reversed())
                .limit(60)
                .toList();

        List<Recipe> ingredientZeroTop = recipes.stream()
                .filter(r -> r.getIngredientMatchScore() == 0.0)
                .sorted(Comparator.comparingDouble(
                        (Recipe r) -> r.getTagMatchScore() * 0.6 + r.getHotScore() * 0.4
                ).reversed())
                .limit(20)
                .toList();

        List<Recipe> candidates = new ArrayList<>();
        candidates.addAll(ingredientPositive);
        candidates.addAll(ingredientZeroTop);

        // 去重
        candidates = new ArrayList<>(
                candidates.stream()
                        .collect(Collectors.toMap(
                                Recipe::getRecipeId,
                                r -> r,
                                (a, b) -> a
                        ))
                        .values()
        );

        // 4. 构建模型特征输入
        List<List<Double>> featureList = new ArrayList<>();
        for (Recipe recipe : candidates) {
            featureList.add(List.of(
                    recipe.getTagMatchScore(),
                    recipe.getIngredientMatchScore(),
                    recipe.getHotScore()
            ));
        }

        // 5. 调用 LightGBM HTTP 服务
        LightGBMRankHttpPredictor predictor =
                new LightGBMRankHttpPredictor("http://localhost:5000");

        List<Double> scores;
        try {
            List<Integer> sessionSeq = userHistoryRepository
                    .findTopNByUserIdOrderByCookTimeDesc(userId, PageRequest.of(0, 5))
                    .stream()
                    .map(UserHistory::getRecipeId)
                    .collect(Collectors.toList());
            List<Integer> candidateIds = candidates.stream()
                    .map(Recipe::getRecipeId)
                    .toList();

            scores = predictor.predictScores(featureList, sessionSeq, candidateIds);
        } catch (Exception e) {
            // 降级：简单加权
            scores = new ArrayList<>();
            for (Recipe recipe : candidates) {
                double score = recipe.getTagMatchScore() * 0.4
                        + recipe.getIngredientMatchScore() * 0.4
                        + recipe.getHotScore() * 0.2;
                scores.add(score);
            }
        }

        // 6. 设置预测分数并排序
        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).setPredictScore(scores.get(i));
        }

        candidates.sort((r1, r2) ->
                Double.compare(r2.getPredictScore(), r1.getPredictScore())
        );

        return candidates;
    }


    /**
     * 只按标签匹配度排序
     */
    private List<Recipe> sortByTagMatchOnly(List<Recipe> recipes, Integer userId) {
        UserFeatureContext ctx = buildUserFeatureContext(userId);

        if (recipes == null || recipes.isEmpty()) {
            return recipes;
        }

        if (userId == null || userId <= 0) {
            // 未登录用户，按热度排序
            recipes.sort((r1, r2) -> Integer.compare(r2.getPopularity(), r1.getPopularity()));
            return recipes;
        }

        Map<Integer, List<RecipeTag>> recipeTagMap =
                recipeTagRepository.findByRecipeIdIn(
                        recipes.stream().map(Recipe::getRecipeId).toList()
                ).stream().collect(Collectors.groupingBy(RecipeTag::getRecipeId));

        Map<Integer, List<Integer>> recipeIngredientMap =
                recipeIngredientRepository.findByRecipeIdIn(
                        recipes.stream().map(Recipe::getRecipeId).toList()
                ).stream().collect(Collectors.groupingBy(
                        RecipeIngredient::getRecipeId,
                        Collectors.mapping(RecipeIngredient::getIngredientId, Collectors.toList())
                ));
        for(Recipe recipe: recipes) {
            recipe.setRecipeTags(recipeTagMap.getOrDefault(recipe.getRecipeId(), List.of()));
            recipe.setIngredientIds(recipeIngredientMap.getOrDefault(recipe.getRecipeId(), List.of()));
        }

        // 计算标签匹配度
        for (Recipe recipe : recipes) {
            double tagScore = scoreCalculationService.calculateTagMatchScore(recipe, ctx);
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
        UserFeatureContext ctx = buildUserFeatureContext(userId);

        if (recipes == null || recipes.isEmpty()) {
            return recipes;
        }

        if (userId == null || userId <= 0) {
            // 未登录用户，按热度排序
            recipes.sort((r1, r2) -> Integer.compare(r2.getPopularity(), r1.getPopularity()));
            return recipes;
        }

        Map<Integer, List<RecipeTag>> recipeTagMap =
                recipeTagRepository.findByRecipeIdIn(
                        recipes.stream().map(Recipe::getRecipeId).toList()
                ).stream().collect(Collectors.groupingBy(RecipeTag::getRecipeId));

        Map<Integer, List<Integer>> recipeIngredientMap =
                recipeIngredientRepository.findByRecipeIdIn(
                        recipes.stream().map(Recipe::getRecipeId).toList()
                ).stream().collect(Collectors.groupingBy(
                        RecipeIngredient::getRecipeId,
                        Collectors.mapping(RecipeIngredient::getIngredientId, Collectors.toList())
                ));
        for(Recipe recipe: recipes) {
            recipe.setRecipeTags(recipeTagMap.getOrDefault(recipe.getRecipeId(), List.of()));
            recipe.setIngredientIds(recipeIngredientMap.getOrDefault(recipe.getRecipeId(), List.of()));
        }

        // 计算原料匹配度
        for (Recipe recipe : recipes) {
            double ingredientScore = scoreCalculationService.calculateIngredientMatchScore(recipe, ctx);
            recipe.setIngredientMatchScore(ingredientScore);
        }

        // 按原料匹配度降序排序
        recipes.sort((r1, r2) -> Double.compare(r2.getIngredientMatchScore(), r1.getIngredientMatchScore()));

        return recipes;
    }

    /**
     * 建立用户特征上下文对象
     * @param userId 用户ID
     * @return 用户特征上下文对象
     */
    private UserFeatureContext buildUserFeatureContext(Integer userId) {

        Set<Integer> userTagSet = new HashSet<>(
                userTagRepository.findTagIdsByUserId(userId)
        );

        Set<Integer> userIngredientSet = userIngredientRepository
                .findByUserIdAndQuantity(userId, 1)
                .stream()
                .map(UserIngredient::getIngredientId)
                .collect(Collectors.toSet());

        Map<Integer, Double> ingredientIdfMap = loadIngredientIdfMap();

        return new UserFeatureContext(
                userTagSet,
                userIngredientSet,
                ingredientIdfMap
        );
    }

    /**
     * 从IDF中加载数据
     * @return IDF映射数据
     */
    private Map<Integer, Double> loadIngredientIdfMap() {

        List<IngredientIdf>     list = ingredientIdfRepository.findAllIdf();
        Map<Integer, Double>    map = new HashMap<>(list.size());

        for(IngredientIdf idf: list) {
            map.put(idf.getIngredientId(), idf.getIdfValue());
        }

        return map;
    }
}