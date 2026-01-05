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

    public Map<String, Object> getRecipesByTagAndPage(Integer tagId, int page, int pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;

        List<Recipe> recipes;
        long total;

        // 根据 tagId 进行过滤
        if (tagId == 0) {
            // 全部菜谱
            recipes = recipeRepository.findAllOrderByPopularityWithPagination(offset, pageSize);
            total = recipeRepository.countAll();
        } else {
            // 按标签查询
            recipes = recipeRepository.findByTagIdOrderByPopularityWithPagination(tagId, offset, pageSize);
            total = recipeRepository.countByTagId(tagId);
        }

        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / pageSize);

        // 返回结果和分页信息
        Map<String, Object> result = new HashMap<>();
        result.put("recipes", recipes);
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
}