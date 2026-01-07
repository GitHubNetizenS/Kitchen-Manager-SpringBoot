package com.kitchen_manager.service;

import com.kitchen_manager.dto.HistoryRecipeDTO;
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
    private final UserIngredientRepository userIngredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    public Recipe getRecipeDetail(Integer recipeId) {
        return recipeRepository.findById(recipeId).orElse(null);
    }

    /**
     * 增加菜谱热度
     */
    @Transactional
    public void incrementPopularity(Integer recipeId) {
        recipeRepository.incrementPopularity(recipeId);
    }

    /**
     * 增加菜谱热度（指定增加值）
     */
    @Transactional
    public void increasePopularity(Integer recipeId, int amount) {
        recipeRepository.increasePopularity(recipeId, amount);
    }

    /**
     * 减少菜谱热度（指定减少值）
     */
    @Transactional
    public void decreasePopularity(Integer recipeId, int amount) {
        recipeRepository.decreasePopularity(recipeId, amount);
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
    }

    @Transactional
    public void removeFavorite(Integer userId, Integer recipeId) {
        favoriteRepository.deleteByUserIdAndRecipeId(userId, recipeId);

        // 取消收藏时减少热度10（确保不小于0）
        decreasePopularity(recipeId, 10);
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
            Map<String, Object> formattedRecipe = new HashMap<>();

            // 复制所有字段
            for (Map.Entry<String, Object> entry : recipeMap.entrySet()) {
                formattedRecipe.put(entry.getKey(), entry.getValue());
            }

            // 确保 isFavorite 字段存在且为 Boolean 类型
            if (!formattedRecipe.containsKey("isFavorite")) {
                formattedRecipe.put("isFavorite", false);
            } else if (formattedRecipe.get("isFavorite") instanceof Number) {
                // 如果数据库返回的是数字类型，转换为 Boolean
                Number favoriteValue = (Number) formattedRecipe.get("isFavorite");
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
}