package com.kitchen_manager.service;

import com.kitchen_manager.common.LightGBMRankPredictor;
import com.kitchen_manager.dto.HistoryRecipeDTO;
import com.kitchen_manager.dto.RecipeWithStatusDTO;
import com.kitchen_manager.entity.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
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

    private final ElasticsearchSyncService elasticsearchSyncService;

    public Recipe getRecipeDetail(Integer recipeId) {
        return recipeRepository.findById(recipeId).orElse(null);
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
        int offset = (page - 1) * pageSize;

        List<Map<String, Object>> recipesWithFavorite;
        long total;

        if (tagId == 0) {
            recipesWithFavorite = recipeRepository.findAllForCandidateSetWithFavoriteStatus(userId, offset, pageSize);
            total = recipeRepository.countAll();
        } else {
            recipesWithFavorite = recipeRepository.findByTagIdForCandidateSetWithFavoriteStatus(tagId, userId, offset, pageSize);
            total = recipeRepository.countByTagId(tagId);
        }

        // 从Map中提取Recipe对象
        List<Recipe> recipes = new ArrayList<>();
        Map<Integer, Boolean> favoriteStatusMap = new HashMap<>();
        Map<Integer, Boolean> cartStatusMap = new HashMap<>();

        for (Map<String, Object> recipeMap : recipesWithFavorite) {
            Recipe recipe = new Recipe();
            recipe.setRecipeId((Integer) recipeMap.get("recipe_id"));
            recipe.setName((String) recipeMap.get("name"));
            recipe.setImageUrl((String) recipeMap.get("image_url"));
            recipe.setTaste((String) recipeMap.get("taste"));
            recipe.setMethod((String) recipeMap.get("method"));
            recipe.setTime((String) recipeMap.get("time"));
            recipe.setDifficulty((String) recipeMap.get("difficulty"));
            recipe.setNeeds((String) recipeMap.get("needs"));
            recipe.setSteps((String) recipeMap.get("steps"));
            recipe.setPopularity((Integer) recipeMap.get("popularity"));

            recipes.add(recipe);

            // 保存收藏状态
            Boolean isFavorite = recipeMap.get("isFavorite") instanceof Number
                    ? ((Number) recipeMap.get("isFavorite")).intValue() == 1
                    : (Boolean) recipeMap.get("isFavorite");
            favoriteStatusMap.put(recipe.getRecipeId(), isFavorite);

            // 保存购物车状态 - 关键修复
            Boolean inShoppingCart = false;
            if (recipeMap.containsKey("inShoppingCart")) {
                Object cartObj = recipeMap.get("inShoppingCart");
                if (cartObj instanceof Number) {
                    inShoppingCart = ((Number) cartObj).intValue() == 1;
                } else if (cartObj instanceof Boolean) {
                    inShoppingCart = (Boolean) cartObj;
                }
            }
            cartStatusMap.put(recipe.getRecipeId(), inShoppingCart);
        }

        // 应用推荐排序
        recipes = sortRecipesByFeatures(recipes, userId);

        // 重新组装带收藏状态和购物车状态的结果
        List<Map<String, Object>> resultRecipes = new ArrayList<>();
        for (Recipe recipe : recipes) {
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
     * 对候选菜谱计算特征并排序
     * @param recipes 候选菜谱列表
     * @param userId 当前用户ID
     * @return 排序后的菜谱列表
     */
    private List<Recipe> sortRecipesByFeatures(List<Recipe> recipes, Integer userId) {
        if(null==recipes || recipes.isEmpty() || null==userId) {

            return recipes;
        }
        // 遍历每个菜谱，计算标签匹配度、原料匹配度、热度特征。
        List<List<Double>> featureList = new ArrayList<>();

        for(Recipe recipe: recipes) {
            // 标签匹配度。
            double tagScore = calculateTagMatchScore(recipe, userId);

            recipe.setTagMatchScore(tagScore);
            // 原料匹配度。
            double ingredientScore = calculateIngredientMatchScore(recipe, userId);

            recipe.setIngredientMatchScore(ingredientScore);
            // 热度特征。
            double hotScore = Math.log(1+recipe.getPopularity());

            recipe.setHotScore(hotScore);

            List<Double> features = new ArrayList<>();

            features.add(tagScore);
            features.add(ingredientScore);
            features.add(hotScore);
            featureList.add(features);
        }
        // 调用 Python 模型预测。
        LightGBMRankPredictor   predictor = new LightGBMRankPredictor("D:\\python3.12\\python.exe", "python\\rank_predictor.py", "python\\lightgbm_rank_model.txt");
        List<Double>            scores;

        try {
            scores = predictor.predictScores(featureList);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("使用简单加权排序！");
            // 出错时回退到简单加权排序。
            scores = new ArrayList<>();
            for(Recipe recipe: recipes) {
                double score = recipe.getTagMatchScore() * 0.4
                        + recipe.getIngredientMatchScore() * 0.4
                        + recipe.getHotScore() * 0.2;

                scores.add(score);
            }
        }
        // 设置分数并排序。
        for(int i=0; i<=recipes.size()-1; ++i) {
            recipes.get(i).setPredictScore(scores.get(i));
        }
        recipes.sort((r1, r2) -> Double.compare(r2.getPredictScore(), r1.getPredictScore()));

        return recipes;
    }

    /**
     * 计算单个菜谱与用户标签的匹配度（余弦相似度）
     * @param recipe 菜谱对象
     * @param userId 用户ID
     * @return 标签匹配度（0~1）
     */
    public double calculateTagMatchScore(Recipe recipe, Integer userId) {
        if(null==recipe || null==userId) return 0.0;

        // 获取用户标签集合。
        List<Integer> userTagIds = userTagRepository.findTagIdsByUserId(userId);

        // 获取菜谱标签及match_amount。
        List<RecipeTag> recipeTags = recipeTagRepository.findByRecipeId(recipe.getRecipeId());

        if (userTagIds.isEmpty() || recipeTags.isEmpty()) return 0.0;

        // 构建向量。
        // 假设标签总数为13维。
        double[] userVector = new double[13];
        double[] recipeVector = new double[13];

        for(Integer tagId: userTagIds) {
            // 用户标签向量值为1。
            userVector[tagId-1] = 1.0;
        }
        for(RecipeTag rt: recipeTags) {
            // 菜谱标签向量值为match_amount。
            recipeVector[rt.getTagId()-1] = rt.getMatchAmount();
        }
        // 计算余弦相似度。
        double dot = 0.0, normUser = 0.0, normRecipe = 0.0;

        for(int i=0; i<=12; ++i) {
            dot += userVector[i] * recipeVector[i];
            normUser += userVector[i] * userVector[i];
            normRecipe += recipeVector[i] * recipeVector[i];
        }
        if(0==normUser || 0==normRecipe) return 0.0;

        return dot / (Math.sqrt(normUser)*Math.sqrt(normRecipe));
    }

    /**
     * 计算用户已有原料和菜谱所需原料的交集占所需原料的比例（原料匹配度）
     * @param recipe 菜谱对象
     * @param userId 用户ID
     * @return 原料匹配度（0~1）
     */
    public double calculateIngredientMatchScore(Recipe recipe, Integer userId) {
        if(null==userId) return 0.0;
        // 获取用户已有原料。
        List<Integer> userIngredientIds = userIngredientRepository
                .findByUserIdAndQuantity(userId, 1)
                .stream()
                .map(UserIngredient::getIngredientId)
                .toList();
        // 获取菜谱所需原料。
        List<Integer> recipeIngredientIds = recipeIngredientRepository
                .findByRecipeId(recipe.getRecipeId())
                .stream()
                .map(RecipeIngredient::getIngredientId)
                .toList();

        if(recipeIngredientIds.isEmpty()) return 0.0;
        // 计算已有原料交集。
        long matchCount = recipeIngredientIds
                .stream()
                .filter(userIngredientIds::contains)
                .count();
        // 计算coverage。
        double coverage = (double)matchCount / recipeIngredientIds.size();
        // 计算 missing_core_ratio（TF-IDF版本）。
        double tf = 1.0 / recipeIngredientIds.size();
        double missingIdfSum = 0.0;
        double totalIdfSum = 0.0;

        for(Integer ingredientId: recipeIngredientIds) {
            double idf = ingredientIdfRepository
                    .findById(ingredientId)
                    .map(IngredientIdf::getIdfValue)
                    .orElse(0.0);

            totalIdfSum += idf * tf;
            if(!userIngredientIds.contains(ingredientId)) {
                missingIdfSum += idf * tf;
            }
        }

        double missingCoreRatio = totalIdfSum==0.0? 0.0: missingIdfSum/totalIdfSum;

        // 最终原料匹配度。
        return coverage * (1.0 - missingCoreRatio);
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

        try {
            if (userId == null) {
                userId = 0;
            }

            // 计算偏移量
            int offset = (page - 1) * pageSize;

            // 先获取过滤后的recipeIds
            List<Integer> filteredRecipeIds = recipeRepository.findFilteredRecipeIds(
                    taste, method, difficulty, offset, pageSize);

            // 获取总数
            long total = recipeRepository.countFilteredRecipes(taste, method, difficulty);

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

        } catch (Exception e) {
            // 如果主方法失败，尝试使用备用方法
            try {
                return getFilteredRecipesWithFavoriteStatusBackup(
                        taste, method, difficulty, sort, userId, page, pageSize);
            } catch (Exception ex) {
                e.printStackTrace();
                throw new RuntimeException("获取过滤菜谱失败: " + e.getMessage());
            }
        }
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
        shoppingListRepository.updateIngredientStatus(userId, recipeId, ingredientId, status);
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
}