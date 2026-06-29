package com.kitchen_manager.service;

import com.kitchen_manager.entity.*;
import com.kitchen_manager.dto.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserIngredientService {
    private final UserIngredientRepository userIngredientRepository;
    private final IngredientRepository ingredientRepository;

    public List<IngredientDetailResponse> getUserIngredients(Integer userId) {
        List<UserIngredient> userIngredients = userIngredientRepository.findByUserIdAndQuantity(userId, 1);
        List<IngredientDetailResponse> responses = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        for (UserIngredient ui : userIngredients) {
            Ingredient ingredient = ingredientRepository.findById(ui.getIngredientId()).orElse(null);
            if (ingredient != null) {
                IngredientDetailResponse response = new IngredientDetailResponse();
                response.setName(ingredient.getName());

                Date storageDate = new Date(ui.getStorageTime().getTime());
                response.setStorageDate(sdf.format(storageDate));

                // 使用自定义保质期或默认保质期
                Integer expiryDays = ui.getCustomExpiryDays() != null ?
                        ui.getCustomExpiryDays() : ingredient.getExpiryDays();
                response.setExpiryDays(expiryDays);

                // 计算到期日
                long expiryInMillis = (long) expiryDays * 24 * 60 * 60 * 1000;
                Date expiryDate = new Date(storageDate.getTime() + expiryInMillis);
                response.setExpiryDate(sdf.format(expiryDate));

                response.setNutrition(ingredient.getNutrition());
                response.setBenefit(ingredient.getHealthBenefit());
                response.setImageUrl(ingredient.getImageUrl());
                response.setCategory(ingredient.getMainCategory());

                responses.add(response);
            }
        }

        return responses;
    }

    @Transactional
    public void updateIngredient(Integer userId, String ingredientName,
                                 String category, String storageDate,
                                 Integer customExpiryDays) throws Exception {

        System.out.println("开始更新食材 - userId: " + userId +
                ", ingredientName: " + ingredientName);

        // 更新食材分类
        Ingredient ingredient = ingredientRepository.findByName(ingredientName)
                .orElseThrow(() -> new RuntimeException("食材不存在"));

        System.out.println("找到食材: " + ingredient.getName() +
                ", ingredientId: " + ingredient.getIngredientId());

        ingredient.setMainCategory(category);
        ingredientRepository.save(ingredient);

        System.out.println("更新了食材分类: " + category);

        // 更新入库时间和自定义保质期
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date = sdf.parse(storageDate);
        Timestamp timestamp = new Timestamp(date.getTime());

        // 查找用户食材记录
        UserIngredient userIngredient = userIngredientRepository
                .findByUserIdAndIngredientId(userId, ingredient.getIngredientId())
                .orElseThrow(() -> new RuntimeException("用户食材记录不存在"));

        System.out.println("找到用户食材记录 - stockId: " + userIngredient.getStockId() +
                ", 当前storageTime: " + userIngredient.getStorageTime() +
                ", 当前customExpiryDays: " + userIngredient.getCustomExpiryDays());

        userIngredient.setStorageTime(timestamp);
        userIngredient.setCustomExpiryDays(customExpiryDays);
        UserIngredient saved = userIngredientRepository.save(userIngredient);

        System.out.println("更新后的用户食材记录 - storageTime: " + saved.getStorageTime() +
                ", customExpiryDays: " + saved.getCustomExpiryDays());
    }
}
