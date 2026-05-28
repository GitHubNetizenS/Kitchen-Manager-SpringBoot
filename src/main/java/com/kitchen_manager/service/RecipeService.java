package com.kitchen_manager.service;

import com.kitchen_manager.common.LightGBMRankHttpPredictor;
import com.kitchen_manager.dto.HistoryRecipeDTO;
import com.kitchen_manager.dto.RecipeWithFavoriteProjection;
import com.kitchen_manager.dto.UserFeatureContext;
import com.kitchen_manager.dto.RecipeWithStatusDTO;
import com.kitchen_manager.entity.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeService {
    private final RecipeRepository recipeRepository;
    private final UserFavoriteRecipeRepository favoriteRepository;
    private final UserHistoryRepository historyRepository;
    private final UserTagRepository userTagRepository;
    private final RecipeTagRepository recipeTagRepository;
    private final UserIngredientRepository userIngredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final IngredientIdfRepository ingredientIdfRepository;
    private final UserShoppingListRepository shoppingListRepository;
    private final RecipeVideoRepository recipeVideoRepository;
    private final SearchService searchService;
    private final UserHistoryRepository userHistoryRepository;

    private final ElasticsearchSyncService elasticsearchSyncService;

    public Recipe getRecipeDetail(Integer recipeId) {
        return recipeRepository.findById(recipeId).orElse(null);
    }

    /**
     * 根据菜谱ID获取视频列表
     */
    public List<RecipeVideo> getVideosByRecipeId(Integer recipeId) {
        try {
            List<RecipeVideo> videos = recipeVideoRepository.findByRecipeId(recipeId);
            return videos;
        } catch (Exception e) {
            throw new RuntimeException("获取视频失败: " + e.getMessage());
        }
    }

    /**
     * 检查菜谱是否有视频
     */
    public boolean hasVideo(Integer recipeId) {
        return recipeVideoRepository.existsByRecipeId(recipeId);
    }

    /**
     * 增加菜谱热度
     */
    @Transactional
    public void incrementPopularity(Integer recipeId) {
        recipeRepository.incrementPopularity(recipeId);
        syncToElasticsearch(recipeId);

    }

    /**
     * 增加菜谱热度（指定增加值）
     */
    @Transactional
    public void increasePopularity(Integer recipeId, int amount) {
        recipeRepository.increasePopularity(recipeId, amount);
        syncToElasticsearch(recipeId);
    }

    /**
     * 减少菜谱热度（指定减少值）
     */
    @Transactional
    public void decreasePopularity(Integer recipeId, int amount) {
        recipeRepository.decreasePopularity(recipeId, amount);
        syncToElasticsearch(recipeId);
    }

    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAllOrderByPopularity();
    }

    @Transactional
    public void addFavorite(Integer userId, Integer recipeId) {
        if (favoriteRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            throw new RuntimeException("您已经收藏过这个菜谱");
        }

        UserFavoriteRecipe favorite = new UserFavoriteRecipe();
        favorite.setUserId(userId);
        favorite.setRecipeId(recipeId);
        favorite.setFavoriteTime(new Timestamp(System.currentTimeMillis()));
        favoriteRepository.save(favorite);

        // 收藏时增加热度10
        increasePopularity(recipeId, 10);
        syncToElasticsearch(recipeId);
    }

    @Transactional
    public void removeFavorite(Integer userId, Integer recipeId) {
        favoriteRepository.deleteByUserIdAndRecipeId(userId, recipeId);

        // 取消收藏时减少热度10（确保不小于0）
        decreasePopularity(recipeId, 10);
        syncToElasticsearch(recipeId);
    }

    public long getFavoriteCount(Integer userId) {
        return favoriteRepository.countByUserId(userId);
    }

    public List<Integer> getFavoriteRecipeIds(Integer userId) {
        return favoriteRepository.findRecipeIdsByUserId(userId);
    }

    /**
     * 获取用户收藏的菜谱列表（支持按时间或匹配值排序）
     */
    public List<Recipe> getFavoriteRecipes(Integer userId, String sortType) {
        if ("time".equals(sortType)) {
            // 按收藏时间排序
            return getFavoriteRecipesByTime(userId);
        } else if ("match".equals(sortType)) {
            // 按匹配值排序
            return getFavoriteRecipesByMatch(userId);
        } else {
            // 默认按时间排序
            return getFavoriteRecipesByTime(userId);
        }
    }

    /**
     * 按收藏时间排序获取菜谱
     */
    private List<Recipe> getFavoriteRecipesByTime(Integer userId) {
        // 获取按时间排序的收藏记录
        List<UserFavoriteRecipe> favorites = favoriteRepository
                .findByUserIdOrderByFavoriteTimeDesc(userId);

        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }

        // 提取菜谱ID（保持时间顺序）
        List<Integer> recipeIds = favorites.stream()
                .map(UserFavoriteRecipe::getRecipeId)
                .collect(Collectors.toList());

        // 获取菜谱详情
        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);

        // 保持原始顺序
        Map<Integer, Recipe> recipeMap = recipes.stream()
                .collect(Collectors.toMap(Recipe::getRecipeId, r -> r));

        return recipeIds.stream()
                .map(recipeMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 按匹配值排序获取菜谱
     */
    private List<Recipe> getFavoriteRecipesByMatch(Integer userId) {
        List<Integer> recipeIds = favoriteRepository.findRecipeIdsByUserId(userId);

        if (recipeIds.isEmpty()) {
            return new ArrayList<>();
        }

        return recipeRepository.findByRecipeIdsOrderByMatchValue(recipeIds, userId);
    }

    @Transactional
    public void addHistory(Integer userId, Integer recipeId) {
        // 检查是否已存在
        if (historyRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            // 如果存在，更新时间
            historyRepository.updateCookTime(userId, recipeId, new Timestamp(System.currentTimeMillis()));
        } else {
            // 不存在，新增
            UserHistory history = new UserHistory();
            history.setUserId(userId);
            history.setRecipeId(recipeId);
            history.setCookTime(new Timestamp(System.currentTimeMillis()));
            historyRepository.save(history);
        }

        // 烹饪后增加热度20
        increasePopularity(recipeId, 20);
        syncToElasticsearch(recipeId);
    }

    private void syncToElasticsearch(Integer recipeId) {
        try {
            elasticsearchSyncService.syncOne(recipeId);
        } catch (Exception e) {
            System.err.println("同步到 Elasticsearch 失败 (recipe_id=" + recipeId + "): " + e.getMessage());
            // 不抛出异常，避免影响主业务
        }
    }

    @Transactional
    public void deleteHistory(Integer userId, Integer recipeId) {
        historyRepository.deleteByUserIdAndRecipeId(userId, recipeId);
    }

    public long getHistoryCount(Integer userId) {
        return historyRepository.countByUserId(userId);
    }

    public List<Integer> getHistoryRecipeIds(Integer userId) {
        return historyRepository.findRecipeIdsByUserId(userId);
    }

    /**
     * 获取包含收藏状态的菜谱列表（分页）
     */
    public Map<String, Object> getRecipesByTagAndPageWithFavoriteStatus(Integer tagId, int page, int pageSize, Integer userId) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;

        List<Map<String, Object>> recipesWithFavorite;
        long total;

        // 根据 tagId 进行过滤
        if (tagId == 0) {
            // 全部菜谱，包含收藏状态
            recipesWithFavorite = recipeRepository.findAllWithFavoriteStatus(userId, offset, pageSize);
            total = recipeRepository.countAll();
        } else {
            // 按标签查询，包含收藏状态
            recipesWithFavorite = recipeRepository.findByTagIdWithFavoriteStatus(tagId, userId, offset, pageSize);
            total = recipeRepository.countByTagId(tagId);
        }

        // 转换数据结构，确保返回格式统一
        List<Map<String, Object>> resultRecipes = new ArrayList<>();
        for (Map<String, Object> recipeMap : recipesWithFavorite) {

            // 复制所有字段
            Map<String, Object> formattedRecipe = new HashMap<>(recipeMap);

            // 确保 isFavorite 字段存在且为 Boolean 类型
            if (!formattedRecipe.containsKey("isFavorite")) {
                formattedRecipe.put("isFavorite", false);
            } else if (formattedRecipe.get("isFavorite") instanceof Number favoriteValue) {
                // 如果数据库返回的是数字类型，转换为 Boolean
                formattedRecipe.put("isFavorite", favoriteValue.intValue() == 1);
            }

            resultRecipes.add(formattedRecipe);
        }

        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / pageSize);

        // 返回结果和分页信息
        Map<String, Object> result = new HashMap<>();
        result.put("recipes", resultRecipes);
        result.put("currentPage", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("totalPages", totalPages);

        return result;
    }

    /**
     * 获取带推荐排序、收藏状态和购物车状态的菜谱列表
     */
    public Map<String, Object> getRecipesByTagAndPageWithRankingAndFavorite(Integer tagId, int page, int pageSize, Integer userId) {
        List<RecipeWithFavoriteProjection> projections;

        if(0==tagId) {
            projections = recipeRepository.findAllForCandidateSetWithFavoriteStatus(userId);
        } else {
            projections = recipeRepository.findByTagIdForCandidateSetWithFavoriteStatus(tagId, userId);
        }
        // 从Map中提取Recipe对象。
        List<Recipe> recipes = new ArrayList<>();
        Map<Integer, Boolean> favoriteStatusMap = new HashMap<>();
        Map<Integer, Boolean> cartStatusMap = new HashMap<>();

        for(RecipeWithFavoriteProjection p: projections) {
            Recipe recipe = new Recipe();

            recipe.setRecipeId(p.getRecipeId());
            recipe.setName(p.getName());
            recipe.setImageUrl(p.getImageUrl());
            recipe.setTaste(p.getTaste());
            recipe.setMethod(p.getMethod());
            recipe.setTime(p.getTime());
            recipe.setDifficulty(p.getDifficulty());
            recipe.setNeeds(p.getNeeds());
            recipe.setSteps(p.getSteps());
            recipe.setPopularity(p.getPopularity());
            recipes.add(recipe);
            // 保存收藏状态。
            favoriteStatusMap.put(recipe.getRecipeId(), 1==p.getIsFavorite());
            cartStatusMap.put(recipe.getRecipeId(), 1==p.getInShoppingCart());
        }

        // 调用排序方法（计算特征+LightGBM Rank）。
        recipes = sortRecipesByFeatures(recipes, userId);
        // 分页处理。
        int             total = recipes.size();
        int             totalPages = (int)Math.ceil((double)total/pageSize);
        int             fromIndex = Math.min((page-1)*pageSize, total);
        int             toIndex = Math.min(page*pageSize, total);
        List<Recipe>    pagedRecipes = recipes.subList(fromIndex, toIndex);
        // 重新组装带收藏状态的结果。

        // 重新组装带收藏状态和购物车状态的结果
        List<Map<String, Object>> resultRecipes = new ArrayList<>();

        for(Recipe recipe: pagedRecipes) {
            Map<String, Object> recipeMap = new HashMap<>();

            recipeMap.put("recipe_id", recipe.getRecipeId());
            recipeMap.put("name", recipe.getName());
            recipeMap.put("image_url", recipe.getImageUrl());
            recipeMap.put("taste", recipe.getTaste());
            recipeMap.put("method", recipe.getMethod());
            recipeMap.put("time", recipe.getTime());
            recipeMap.put("difficulty", recipe.getDifficulty());
            recipeMap.put("needs", recipe.getNeeds());
            recipeMap.put("steps", recipe.getSteps());
            recipeMap.put("popularity", recipe.getPopularity());
            recipeMap.put("isFavorite", favoriteStatusMap.getOrDefault(recipe.getRecipeId(), false));
            // 关键：添加购物车状态
            recipeMap.put("inShoppingCart", cartStatusMap.getOrDefault(recipe.getRecipeId(), false));

            resultRecipes.add(recipeMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("recipes", resultRecipes);
        result.put("currentPage", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("totalPages", totalPages);

        return result;
    }

    /**
     * 对候选菜谱计算特征并排序
     * @param recipes 候选菜谱列表
     * @param userId 当前用户ID
     * @return 排序后的菜谱列表
     */
    private List<Recipe> sortRecipesByFeatures(List<Recipe> recipes, Integer userId) {
        UserFeatureContext ctx = buildUserFeatureContext(userId);

        if(null==recipes || recipes.isEmpty() || null==userId) {

            return recipes;
        }
        // 遍历每个菜谱，计算标签匹配度、原料匹配度、热度特征。
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

        for(Recipe recipe: recipes) {
            // 标签匹配度。
            double tagScore = calculateTagMatchScore(recipe, ctx);
            // 原料匹配度。
            double ingredientScore = calculateIngredientMatchScore(recipe, ctx);
            // 热度特征。
            double hotScore = Math.log(1+recipe.getPopularity()) / 10.0;

            recipe.setTagMatchScore(tagScore);
            recipe.setIngredientMatchScore(ingredientScore);
            recipe.setHotScore(hotScore);
        }

        List<Recipe> ingredientPositive = recipes.stream()
                .filter(r -> r.getIngredientMatchScore() > 0.0)
                .sorted(Comparator.comparingDouble(Recipe::getIngredientMatchScore).reversed())
                .limit(60)
                .toList();
        List<Recipe> candidates = new ArrayList<>(ingredientPositive);
        List<Recipe> ingredientZeroTop = recipes.stream()
                .filter(r -> r.getIngredientMatchScore()==0.0)
                .sorted(Comparator.comparingDouble(
                        (Recipe r) -> r.getTagMatchScore()*0.6 + r.getHotScore()*0.4
                ).reversed())
                .limit(20)
                .toList();

        candidates.addAll(ingredientZeroTop);
        candidates = new ArrayList<>(
                candidates.stream()
                .collect(Collectors.toMap(
                        Recipe::getRecipeId,
                        r -> r,
                        (a, b) -> a
                ))
                .values()
        );

        List<List<Double>> featureList = new ArrayList<>();

        for(Recipe recipe: candidates) {
            // 特征增强：必须与generate_csv.py中的变换完全一致
            double tagScoreEnhanced = Math.pow(recipe.getTagMatchScore(), 0.5) * 1.5;
            double ingredientScoreEnhanced = Math.pow(recipe.getIngredientMatchScore(), 1.2);
            double hotScoreEnhanced = recipe.getHotScore() * 5.0;

            featureList.add(List.of(
                    tagScoreEnhanced,
                    ingredientScoreEnhanced,
                    hotScoreEnhanced
            ));
        }

        // 调用 Python 模型预测。
        LightGBMRankHttpPredictor predictor = new LightGBMRankHttpPredictor("http://python-model:5000");
        List<Double>            scores;

        try {
            long startTime = System.currentTimeMillis();
            // 在 featureList 构造完成后新增
            List<Integer> sessionSeq = userHistoryRepository
                    .findTopNByUserIdOrderByCookTimeDesc(userId, PageRequest.of(0, 5))
                    .stream()
                    .map(UserHistory::getRecipeId)
                    .collect(Collectors.toList());
            Collections.reverse(sessionSeq);  // 还原为时间正序

            List<Integer> candidateIds = candidates.stream()
                    .map(Recipe::getRecipeId)
                    .toList();
            scores = predictor.predictScores(featureList, sessionSeq, candidateIds);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            System.out.println("predictScores 执行时间: " + duration + " 毫秒");
            System.out.println("predictScores 执行时间: " + (duration / 1000.0) + " 秒");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("Python模型调用失败，启动备用措施：简单加权排序。");
            // 出错时回退到简单加权排序。
            scores = new ArrayList<>();
            for(Recipe recipe: candidates) {
                double score = recipe.getTagMatchScore() * 0.4
                        + recipe.getIngredientMatchScore() * 0.4
                        + recipe.getHotScore() * 0.2;

                scores.add(score);
            }
        }
        // 设置分数并排序。
        for(int i=0; i<=candidates.size()-1; ++i) {
            candidates.get(i).setPredictScore(scores.get(i));
        }
        candidates.sort((r1, r2) -> Double.compare(r2.getPredictScore(), r1.getPredictScore()));

        return candidates;
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
     * 计算单个菜谱与用户标签的匹配度（余弦相似度）
     *
     * @param recipe 菜谱对象
     * @param ctx    用户上下文
     * @return 标签匹配度（0~1）
     */
    double calculateTagMatchScore(Recipe recipe, UserFeatureContext ctx) {
        if (recipe == null || ctx == null) {
            return 0.0;
        }

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
     * 计算用户已有原料和菜谱所需原料的交集占所需原料的比例（原料匹配度）
     * @param recipe 菜谱对象
     * @param ctx 用户上下文
     * @return 原料匹配度（0~1）
     */
    double calculateIngredientMatchScore(Recipe recipe, UserFeatureContext ctx) {
        if(null==recipe || null==ctx) {

            return -1.0;
        }

        Set<Integer>            userIngredientIds = ctx.getUserIngredientSet();
        Map<Integer, Double>    idfMap = ctx.getIngredientIdfMap();
        List<Integer>           recipeIngredientIds = recipe.getIngredientIds();

        if(null==recipeIngredientIds || recipeIngredientIds.isEmpty()) {

            return -1.0;
        }
        if (userIngredientIds == null || userIngredientIds.isEmpty()) {

            return 0.0;
        }

        int matchCount = 0;

        for(Integer ing: recipeIngredientIds) {
            if (userIngredientIds.contains(ing)) {
                ++matchCount;
            }
        }

        double coverage = (double)matchCount / recipeIngredientIds.size();

        if(coverage<0.15) {

            return coverage * 0.01;
        }

        double tf = 1.0 / recipeIngredientIds.size();
        double missingIdfSum = 0.0;
        double totalIdfSum = 0.0;

        for(Integer ing: recipeIngredientIds) {
            double idf = idfMap.getOrDefault(ing, 1.0);
            double weight = idf * tf;

            totalIdfSum += weight;
            if(!userIngredientIds.contains(ing)) {
                missingIdfSum += weight;
            }
        }
        double missingCoreRatio = totalIdfSum>0.0 ?missingIdfSum/totalIdfSum :1.0;

        return coverage*(1.0-missingCoreRatio);
    }

    /**
     * 加载用户特征上下文
     * @param userId 用户ID
     * @return 用户特征上下文
     */
    private UserFeatureContext loadUserFeatureContext(Integer userId) {

        // 一次性查用户标签。
        Set<Integer> userTags = new HashSet<>(userTagRepository.findTagIdsByUserId(userId));
        // 一次性查用户原料。
        Set<Integer> userIngredients = new HashSet<>(userIngredientRepository.findIngredientIdsByUserId(userId));
        // 一次性查IDF（建议后续做缓存）。
        Map<Integer, Double> idfMap = loadIngredientIdfMap();

        return new UserFeatureContext(userTags, userIngredients, idfMap);
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


    /**
     * 按烹饪时间排序获取历史菜谱（包含历史记录ID和烹饪时间）
     */
    private List<HistoryRecipeDTO> getHistoryRecipesByTime(Integer userId) {
        // 获取用户的所有历史记录
        List<UserHistory> histories = historyRepository.findByUserIdOrderByCookTimeDesc(userId);

        if (histories.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集菜谱ID
        List<Integer> recipeIds = histories.stream()
                .map(UserHistory::getRecipeId)
                .collect(Collectors.toList());

        // 批量查询菜谱信息
        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);
        Map<Integer, Recipe> recipeMap = recipes.stream()
                .collect(Collectors.toMap(Recipe::getRecipeId, recipe -> recipe));

        // 创建DTO列表，保持时间顺序
        List<HistoryRecipeDTO> result = new ArrayList<>();
        for (UserHistory history : histories) {
            Recipe recipe = recipeMap.get(history.getRecipeId());
            if (recipe != null) {
                // 创建DTO对象，包含历史记录ID、菜谱信息和烹饪时间
                HistoryRecipeDTO dto = new HistoryRecipeDTO();
                dto.setHistoryId(history.getId());
                dto.setRecipe(recipe);
                dto.setCookTime(history.getCookTime()); // 关键：设置烹饪时间
                result.add(dto);
            }
        }

        return result;
    }

    /**
     * 按匹配值排序获取历史菜谱（包含历史记录ID和烹饪时间）
     */
    private List<HistoryRecipeDTO> getHistoryRecipesByMatch(Integer userId) {
        List<UserHistory> histories = historyRepository.findByUserIdOrderByCookTimeDesc(userId);

        if (histories.isEmpty()) {
            return new ArrayList<>();
        }

        // 提取菜谱ID
        List<Integer> recipeIds = histories.stream()
                .map(UserHistory::getRecipeId)
                .collect(Collectors.toList());

        // 查询按匹配值排序的菜谱
        List<Recipe> recipes = recipeRepository.findByRecipeIdsOrderByMatchValue(recipeIds, userId);

        // 创建菜谱ID到历史记录的映射（用于获取烹饪时间）
        Map<Integer, UserHistory> historyMap = new HashMap<>();
        for (UserHistory history : histories) {
            // 如果有重复菜谱，取最近的一条记录
            if (!historyMap.containsKey(history.getRecipeId())) {
                historyMap.put(history.getRecipeId(), history);
            }
        }

        // 创建DTO列表
        List<HistoryRecipeDTO> result = new ArrayList<>();
        for (Recipe recipe : recipes) {
            UserHistory history = historyMap.get(recipe.getRecipeId());
            if (history != null) {
                HistoryRecipeDTO dto = new HistoryRecipeDTO();
                dto.setHistoryId(history.getId());
                dto.setRecipe(recipe);
                dto.setCookTime(history.getCookTime()); // 关键：设置烹饪时间
                result.add(dto);
            }
        }

        return result;
    }

    /**
     * 获取用户烹饪历史菜谱列表（支持按时间或匹配值排序）
     */
    public List<HistoryRecipeDTO> getHistoryRecipes(Integer userId, String sortType) {
        if ("time".equals(sortType)) {
            return getHistoryRecipesByTime(userId);
        } else if ("match".equals(sortType)) {
            return getHistoryRecipesByMatch(userId);
        } else {
            return getHistoryRecipesByTime(userId);
        }
    }

    /**
     * 删除历史记录（基于历史记录ID）
     */
    @Transactional
    public void deleteHistory(Integer historyId) {
        historyRepository.deleteById(historyId);
    }

    /**
     * 获取菜谱详情（包含收藏状态）
     */
    public Map<String, Object> getRecipeDetailWithFavoriteStatus(Integer recipeId, Integer userId) {
        if (userId == null) {
            userId = 0; // 未登录用户
        }

        Map<String, Object> recipeMap = recipeRepository.findByIdWithFavoriteStatus(recipeId, userId);

        if (recipeMap == null) {
            return null;
        }

        // 确保 isFavorite 字段存在
        if (!recipeMap.containsKey("isFavorite")) {
            recipeMap.put("isFavorite", false);
        }

        return recipeMap;
    }

    /*检查收藏状态*/
    public boolean checkIfRecipeIsFavorite(Integer userId, Integer recipeId) {
        return favoriteRepository.existsByUserIdAndRecipeId(userId, recipeId);
    }

    /**
     * 获取过滤后的菜谱（包含收藏状态）
     * 支持口味多选：单选时包含该口味的所有菜谱，多选时包含所有选中口味的菜谱
     */
    public Map<String, Object> getFilteredRecipesWithFavoriteStatus(
            String taste, String method, String difficulty, String sort,
            Integer userId, int page, int pageSize) {

        // 处理taste: 逗号分隔转list
        List<String> tasteList = taste.isEmpty() ? new ArrayList<>() : Arrays.asList(taste.split(","));

        // 1. 获取所有菜谱（或根据其他条件筛选）
        List<Recipe> allRecipes = recipeRepository.findAll();

        // 2. 应用筛选条件（使用之前的交集逻辑）
        List<Recipe> filteredRecipes = allRecipes.stream()
                .filter(r -> tasteList.isEmpty() ||
                        (r.getTaste() != null && tasteList.stream().allMatch(t -> r.getTaste().contains(t))))
                .filter(r -> method.isEmpty() || (r.getMethod() != null && r.getMethod().contains(method)))
                .filter(r -> difficulty.isEmpty() || (r.getDifficulty() != null && r.getDifficulty().contains(difficulty)))
                .collect(Collectors.toList());

        // 3. 继续使用SearchService排序
        List<Recipe> sortedRecipes = searchService.sortFilteredRecipes(filteredRecipes, sort, userId);

        // 3. 分页处理
        int total = sortedRecipes.size();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<Recipe> pagedRecipes = sortedRecipes.subList(from, to);

        // 4. 获取收藏和购物车状态
        List<Integer> recipeIds = pagedRecipes.stream()
                .map(Recipe::getRecipeId)
                .collect(Collectors.toList());

        List<Map<String, Object>> recipesWithStatus =
                recipeRepository.findByRecipeIdsWithFavoriteAndCartStatus(recipeIds, userId);

        // 5. 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("recipes", recipesWithStatus);
        result.put("total_count", total);
        result.put("current_page", page);
        result.put("page_size", pageSize);
        result.put("total_pages", (int) Math.ceil((double) total / pageSize));

        return result;
    }

    /**
     * 备用方法：使用简单的查询逻辑
     */
    private Map<String, Object> getFilteredRecipesWithFavoriteStatusBackup(
            String taste, String method, String difficulty, String sort,
            Integer userId, int page, int pageSize) {

        // 计算偏移量
        int offset = (page - 1) * pageSize;

        // 使用备用查询方法
        List<Integer> filteredRecipeIds = recipeRepository.findFilteredRecipeIdsSimple(
                taste, method, difficulty, offset, pageSize);

        // 获取总数
        long total = recipeRepository.countFilteredRecipesSimple(
                taste, method, difficulty);

        if (filteredRecipeIds.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("recipes", new ArrayList<>());
            result.put("currentPage", page);
            result.put("pageSize", pageSize);
            result.put("total", total);
            result.put("totalPages", (int) Math.ceil((double) total / pageSize));
            return result;
        }

        // 根据sort排序recipeIds
        List<Integer> sortedRecipeIds = sortRecipeIds(filteredRecipeIds, sort, userId);

        // 获取菜谱详情（带收藏状态）
        List<Map<String, Object>> recipes = recipeRepository.findByIdsWithFavoriteStatus(sortedRecipeIds, userId);

        // 格式化isFavorite为boolean
        List<Map<String, Object>> formattedRecipes = new ArrayList<>();
        for (Map<String, Object> recipe : recipes) {
            Map<String, Object> mutableRecipe = new HashMap<>(recipe);
            Object favoriteValue = mutableRecipe.get("isFavorite");
            boolean isFavorite = false;
            if (favoriteValue != null) {
                if (favoriteValue instanceof Number) {
                    isFavorite = ((Number) favoriteValue).intValue() == 1;
                } else if (favoriteValue instanceof Boolean) {
                    isFavorite = (Boolean) favoriteValue;
                }
            }
            mutableRecipe.put("isFavorite", isFavorite);
            formattedRecipes.add(mutableRecipe);
        }

        int totalPages = (int) Math.ceil((double) total / pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("recipes", formattedRecipes);
        result.put("currentPage", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("totalPages", totalPages);

        return result;
    }

    /**
     * 排序recipeIds
     */
    private List<Integer> sortRecipeIds(List<Integer> recipeIds, String sort, Integer userId) {
        if ("tag_match".equals(sort)) {
            // 标签匹配：按用户标签匹配值降序
            List<Recipe> sortedRecipes = recipeRepository.findByRecipeIdsOrderByMatchValue(recipeIds, userId);
            return sortedRecipes.stream().map(Recipe::getRecipeId).collect(Collectors.toList());
        } else if ("ingredient_match".equals(sort)) {
            // 原料匹配：按用户库存匹配数降序
            return recipeIds.stream()
                    .sorted(Comparator.comparingInt(id -> -getMatchingIngredientCount(userId, id)))
                    .collect(Collectors.toList());
        } else {
            // 综合：按popularity降序
            List<Recipe> sortedRecipes = recipeRepository.findByIdsOrderByPopularity(recipeIds);
            return sortedRecipes.stream().map(Recipe::getRecipeId).collect(Collectors.toList());
        }
    }

    /**
     * 获取用户库存与菜谱食材的匹配数量
     */
    private int getMatchingIngredientCount(Integer userId, Integer recipeId) {
        try {
            // 获取菜谱需要的食材ID列表
            List<Integer> recipeIngredientIds = recipeIngredientRepository.findIngredientIdsByRecipeId(recipeId);

            if (recipeIngredientIds.isEmpty()) {
                return 0;
            }

            // 获取用户拥有的食材ID列表
            List<Integer> userIngredientIds = userIngredientRepository.findIngredientIdsByUserId(userId);

            if (userIngredientIds.isEmpty()) {
                return 0;
            }

            // 计算交集数量
            Set<Integer> recipeSet = new HashSet<>(recipeIngredientIds);
            Set<Integer> userSet = new HashSet<>(userIngredientIds);
            recipeSet.retainAll(userSet); // 求交集

            return recipeSet.size();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 将菜谱所需食材加入购物车
     */
    @Transactional
    public void addToShoppingCart(Integer userId, Integer recipeId) {
        // 检查是否已存在
        boolean alreadyExists = shoppingListRepository.existsByUserIdAndRecipeId(userId, recipeId);

        if (alreadyExists) {
            // 如果已存在，移除（实现切换效果）
            shoppingListRepository.deleteByUserIdAndRecipeId(userId, recipeId);
            return; // 这里可以返回特定信息，或者让前端根据状态判断
        }

        // 如果不存在，添加
        int addedCount = shoppingListRepository.addRecipeIngredientsToCart(userId, recipeId);

        if (addedCount == 0) {
            throw new RuntimeException("该菜谱没有食材可添加");
        }
    }

    /**
     * 从购物车移除菜谱
     */
    @Transactional
    public void removeFromShoppingCart(Integer userId, Integer recipeId) {
        shoppingListRepository.deleteByUserIdAndRecipeId(userId, recipeId);
    }

    /**
     * 检查菜谱是否在购物车中
     */
    public boolean isRecipeInCart(Integer userId, Integer recipeId) {
        return shoppingListRepository.existsByUserIdAndRecipeId(userId, recipeId);
    }

    /**
     * 获取用户在购物车中的菜谱ID列表
     */
    public List<Integer> getShoppingCartRecipeIds(Integer userId) {
        return shoppingListRepository.findRecipeIdsByUserId(userId);
    }

    /**
     * 切换菜谱的购物车状态（添加/移除）
     */
    @Transactional
    public void toggleShoppingCart(Integer userId, Integer recipeId) {
        if (shoppingListRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            // 如果已经存在，移除购物车
            shoppingListRepository.deleteByUserIdAndRecipeId(userId, recipeId);
        } else {
            // 如果不存在，加入购物车
            int addedCount = shoppingListRepository.addRecipeIngredientsToCart(userId, recipeId);

            if (addedCount == 0) {
                throw new RuntimeException("该菜谱没有食材可添加");
            }
        }
    }

    /**
     * 获取带收藏和购物车状态的菜谱列表
     */
    public Map<String, Object> getRecipesByTagAndPageWithFavoriteAndCartStatus(Integer tagId, int page, int pageSize, Integer userId) {
        int offset = (page - 1) * pageSize;

        List<Map<String, Object>> recipesWithStatus;
        long total;

        if (tagId == 0) {
            recipesWithStatus = recipeRepository.findAllWithFavoriteAndCartStatus(userId, offset, pageSize);
            total = recipeRepository.countAll();
        } else {
            recipesWithStatus = recipeRepository.findByTagIdWithFavoriteAndCartStatus(tagId, userId, offset, pageSize);
            total = recipeRepository.countByTagId(tagId);
        }

        // 转换为DTO列表
        List<RecipeWithStatusDTO> resultRecipes = new ArrayList<>();
        for (Map<String, Object> recipeMap : recipesWithStatus) {
            RecipeWithStatusDTO dto = new RecipeWithStatusDTO();

            // 设置基本字段
            if (recipeMap.containsKey("recipe_id")) {
                dto.setRecipeId(((Number) recipeMap.get("recipe_id")).intValue());
            }
            if (recipeMap.containsKey("name")) {
                dto.setName((String) recipeMap.get("name"));
            }
            if (recipeMap.containsKey("image_url")) {
                dto.setImageUrl((String) recipeMap.get("image_url"));
            }
            if (recipeMap.containsKey("taste")) {
                dto.setTaste((String) recipeMap.get("taste"));
            }
            if (recipeMap.containsKey("method")) {
                dto.setMethod((String) recipeMap.get("method"));
            }
            if (recipeMap.containsKey("time")) {
                dto.setTime((String) recipeMap.get("time"));
            }
            if (recipeMap.containsKey("difficulty")) {
                dto.setDifficulty((String) recipeMap.get("difficulty"));
            }
            if (recipeMap.containsKey("needs")) {
                dto.setNeeds((String) recipeMap.get("needs"));
            }
            if (recipeMap.containsKey("steps")) {
                dto.setSteps((String) recipeMap.get("steps"));
            }
            if (recipeMap.containsKey("popularity")) {
                dto.setPopularity(((Number) recipeMap.get("popularity")).intValue());
            }

            // 设置状态字段
            if (recipeMap.containsKey("isFavorite")) {
                Object favoriteObj = recipeMap.get("isFavorite");
                dto.setIsFavorite(parseBooleanValue(favoriteObj));
            }

            if (recipeMap.containsKey("inShoppingCart")) {
                Object cartObj = recipeMap.get("inShoppingCart");
                dto.setInShoppingCart(parseBooleanValue(cartObj));
            }

            resultRecipes.add(dto);
        }

        int totalPages = (int) Math.ceil((double) total / pageSize);
        Map<String, Object> result = new HashMap<>();
        result.put("recipes", resultRecipes);
        result.put("currentPage", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("totalPages", totalPages);

        return result;
    }

    /**
     * 解析布尔值（支持多种类型）
     */
    private Boolean parseBooleanValue(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).intValue() == 1;
        } else if (obj instanceof String) {
            String str = ((String) obj).trim().toLowerCase();
            return str.equals("true") || str.equals("1") || str.equals("y");
        }
        return false;
    }

    /**
     * 获取用户购物车中的菜谱列表（按菜谱分组）
     */
    public List<Map<String, Object>> getShoppingCartRecipes(Integer userId) {
        List<Object[]> results = UserShoppingListRepository.findGroupedShoppingCartByUserId(userId);
        List<Map<String, Object>> recipes = new ArrayList<>();

        for (Object[] row : results) {
            Map<String, Object> recipeMap = new HashMap<>();
            recipeMap.put("recipeId", row[0]);
            recipeMap.put("recipeName", row[1]);
            recipeMap.put("imageUrl", row[2]);
            recipeMap.put("ingredientList", row[3]);
            recipeMap.put("totalIngredients", row[4]);
            recipeMap.put("purchasedCount", row[5]);

            // 计算购买进度
            int total = ((Number) row[4]).intValue();
            int purchased = ((Number) row[5]).intValue();
            recipeMap.put("progress", total > 0 ? (purchased * 100 / total) : 0);

            recipes.add(recipeMap);
        }

        return recipes;
    }

    /**
     * 更新购物车中食材的购买状态
     */
    @Transactional
    public void updateCartIngredientStatus(Integer userId, Integer recipeId,
                                           Integer ingredientId, String status) {
        try {
            // 1. 直接使用小写字符串，因为数据库里存的就是小写
            String statusLowerCase = status.toLowerCase();

            if (!"pending".equals(statusLowerCase) && !"purchased".equals(statusLowerCase)) {
                throw new RuntimeException("无效的状态值: " + status);
            }

            System.out.println("更新状态为: " + statusLowerCase);

            // 2. 更新购物车状态
            shoppingListRepository.updateIngredientStatus(userId, recipeId, ingredientId, statusLowerCase);

            // 3. 如果状态为'purchased'，将食材添加到用户库存
            if ("purchased".equals(statusLowerCase)) {
                addOrUpdateUserIngredient(userId, ingredientId);
            }

        } catch (Exception e) {
            throw new RuntimeException("更新状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除购物车中的食材
     */
    @Transactional
    public void deleteCartIngredient(Integer userId, Integer recipeId, Integer ingredientId) {
        shoppingListRepository.deleteIngredientFromCart(userId, recipeId, ingredientId);
    }

    /**
     * 批量更新购物车中某个菜谱的所有食材状态
     */
    @Transactional
    public void updateAllIngredientsStatus(Integer userId, Integer recipeId, String status) {
        // 获取该菜谱在购物车中的所有食材
        List<Object[]> ingredients = shoppingListRepository.findShoppingCartDetailsByUserId(userId);

        for (Object[] ingredient : ingredients) {
            Integer currentRecipeId = (Integer) ingredient[0];
            Integer currentIngredientId = (Integer) ingredient[3];

            if (currentRecipeId.equals(recipeId)) {
                shoppingListRepository.updateIngredientStatus(
                        userId, recipeId, currentIngredientId, status
                );
            }
        }
    }

    /**
     * 获取用户购物车中的菜谱（按菜谱分组），包含食材详情
     */
    public List<Map<String, Object>> getGroupedShoppingCartByUserId(Integer userId) {
        // 调用新的查询方法获取详细数据
        List<Object[]> results = shoppingListRepository.findGroupedShoppingCartDetails(userId);
        List<Map<String, Object>> groupedRecipes = new ArrayList<>();

        // 按菜谱ID分组
        Map<Integer, Map<String, Object>> recipeMap = new HashMap<>();

        for (Object[] row : results) {
            Integer recipeId = ((Number) row[0]).intValue();

            // 如果这个菜谱还没有添加到map中
            if (!recipeMap.containsKey(recipeId)) {
                Map<String, Object> recipeInfo = new HashMap<>();
                recipeInfo.put("recipeId", recipeId);
                recipeInfo.put("recipeName", row[1]);
                recipeInfo.put("imageUrl", row[2]);
                recipeInfo.put("ingredients", new ArrayList<Map<String, Object>>());
                recipeMap.put(recipeId, recipeInfo);
                // 获取并保存添加时间
                if (row.length > 6 && row[6] != null) {
                    recipeInfo.put("latestAddedTime", row[6]);
                }
            }

            // 添加食材信息
            Map<String, Object> ingredientInfo = new HashMap<>();
            ingredientInfo.put("ingredientId", ((Number) row[3]).intValue());
            ingredientInfo.put("ingredientName", row[4]);
            ingredientInfo.put("status", row[5]);
            ingredientInfo.put("isPurchased", "purchased".equals(row[5]));


            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ingredients = (List<Map<String, Object>>) recipeMap.get(recipeId).get("ingredients");
            ingredients.add(ingredientInfo);
        }

        // 计算每个菜谱的购买进度并添加到最终列表
        for (Map<String, Object> recipeInfo : recipeMap.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ingredients = (List<Map<String, Object>>) recipeInfo.get("ingredients");

            int totalIngredients = ingredients.size();
            int purchasedCount = 0;

            for (Map<String, Object> ingredient : ingredients) {
                if ("purchased".equals(ingredient.get("status"))) {
                    purchasedCount++;
                }
            }

            recipeInfo.put("totalIngredients", totalIngredients);
            recipeInfo.put("purchasedCount", purchasedCount);
            recipeInfo.put("progress", totalIngredients > 0 ? (purchasedCount * 100 / totalIngredients) : 0);

            groupedRecipes.add(recipeInfo);
        }

        return groupedRecipes;
    }

    /**
     * 添加或更新用户库存中的食材
     */
    @Transactional
    public void addOrUpdateUserIngredient(Integer userId, Integer ingredientId) {
        try {
            // 检查是否已存在
            Optional<UserIngredient> existingIngredient =
                    userIngredientRepository.findByUserIdAndIngredientId(userId, ingredientId);

            Timestamp now = new Timestamp(System.currentTimeMillis());

            if (existingIngredient.isPresent()) {
                // 如果已存在，更新存储时间（覆盖）
                UserIngredient userIngredient = existingIngredient.get();
                userIngredient.setStorageTime(now);
                userIngredient.setQuantity(1);
                userIngredientRepository.save(userIngredient);
                System.out.println("更新了用户 " + userId + " 的食材 " + ingredientId + " 库存时间");
            } else {
                // 如果不存在，创建新记录
                UserIngredient userIngredient = new UserIngredient();
                userIngredient.setUserId(userId);
                userIngredient.setIngredientId(ingredientId);
                userIngredient.setQuantity(1);
                userIngredient.setStorageTime(now);
                userIngredient.setCustomExpiryDays(null);

                userIngredientRepository.save(userIngredient);
                System.out.println("为用户 " + userId + " 添加了食材 " + ingredientId + " 到库存");
            }
        } catch (Exception e) {
            throw new RuntimeException("更新用户库存失败: " + e.getMessage(), e);
        }
    }

}