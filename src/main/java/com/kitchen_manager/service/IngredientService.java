package com.kitchen_manager.service;

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
public class IngredientService {
    private final IngredientRepository ingredientRepository;
    private final UserIngredientRepository userIngredientRepository;

    // 缓存机制
    private Map<String, Integer> ingredientCache = null;
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 300000; // 5分钟

    @Transactional
    public int addIngredients(Integer userId, List<String> ingredientNames) {
        Map<String, Integer> ingredientMap = loadIngredientMap();
        int processedCount = 0;

        for (String name : ingredientNames) {
            int matchedId = findBestMatch(name, ingredientMap);
            if (matchedId != -1) {
                processedCount += upsertUserIngredient(userId, matchedId);
            }
        }
        return processedCount;
    }
    /**
     * 更新或插入用户食材关联信息
     * @param userId 用户ID
     * @param ingredientId 食材ID
     * @return 1表示成功，0表示失败
     */
    private int upsertUserIngredient(Integer userId, Integer ingredientId) {
        try {
            // 1. 先查询是否已存在该用户和食材的关联
            List<UserIngredient> existingItems = userIngredientRepository.findAll().stream()
                    .filter(ui -> ui.getUserId().equals(userId) && ui.getIngredientId().equals(ingredientId))
                    .collect(Collectors.toList());

            if (!existingItems.isEmpty()) {
                // 2. 如果已存在，更新第一条记录
                UserIngredient existingItem = existingItems.get(0);
                existingItem.setStorageTime(new Timestamp(System.currentTimeMillis()));
                existingItem.setQuantity(1);
                userIngredientRepository.save(existingItem);
                return 1;
            } else {
                // 3. 如果不存在，创建新记录
                UserIngredient ui = new UserIngredient();
                ui.setUserId(userId);
                ui.setIngredientId(ingredientId);
                ui.setQuantity(1);
                ui.setStorageTime(new Timestamp(System.currentTimeMillis()));
                userIngredientRepository.save(ui);
                return 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    @Transactional
    public void deleteUserIngredient(Integer userId, String ingredientName) {
        userIngredientRepository.deleteByUserIdAndIngredientName(userId, ingredientName);
    }

    @Transactional
    public void deleteUserIngredients(Integer userId, List<Integer> ingredientIds) {
        userIngredientRepository.deleteByUserIdAndIngredientIdIn(userId, ingredientIds);
    }

    private Map<String, Integer> loadIngredientMap() {
        long currentTime = System.currentTimeMillis();
        if (ingredientCache != null && (currentTime - lastCacheUpdate < CACHE_DURATION)) {
            return ingredientCache;
        }

        Map<String, Integer> newCache = new HashMap<>();
        List<Ingredient> ingredients = ingredientRepository.findAll();
        for (Ingredient ing : ingredients) {
            newCache.put(ing.getName(), ing.getIngredientId());
        }

        ingredientCache = newCache;
        lastCacheUpdate = currentTime;
        return newCache;
    }

    private int findBestMatch(String input, Map<String, Integer> ingredientMap) {
        if (ingredientMap.containsKey(input)) {
            return ingredientMap.get(input);
        }

        int bestMatchId = -1;
        double bestScore = 0;

        for (Map.Entry<String, Integer> entry : ingredientMap.entrySet()) {
            double score = calculateSimilarity(input, entry.getKey());
            if (score > bestScore) {
                bestScore = score;
                bestMatchId = entry.getValue();
            }
        }

        return bestScore >= 0.7 ? bestMatchId : -1;
    }

    private double calculateSimilarity(String s1, String s2) {
        String clean1 = s1.replaceAll("[\\s\\p{Punct}]", "");
        String clean2 = s2.replaceAll("[\\s\\p{Punct}]", "");

        int len1 = clean1.length();
        int len2 = clean2.length();

        if (len1 == 0 || len2 == 0) return 0.0;

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (clean1.charAt(i - 1) == clean2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }

        int maxLen = Math.max(len1, len2);
        return 1.0 - (double) dp[len1][len2] / maxLen;
    }
}