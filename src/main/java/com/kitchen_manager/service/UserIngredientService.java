package com.kitchen_manager.service;

import com.kitchen_manager.entity.*;
import com.kitchen_manager.dto.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserIngredientService {
    private final UserIngredientRepository userIngredientRepository;
    private final IngredientRepository ingredientRepository;

    public List<IngredientDetailResponse> getUserIngredients(Integer userId) {
        List<UserIngredient> userIngredients = userIngredientRepository.findByUserId(userId);
        List<IngredientDetailResponse> responses = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        for (UserIngredient ui : userIngredients) {
            Ingredient ingredient = ingredientRepository.findById(ui.getIngredientId()).orElse(null);
            if (ingredient != null) {
                IngredientDetailResponse response = new IngredientDetailResponse();
                response.setName(ingredient.getName());

                Date storageDate = new Date(ui.getStorageTime().getTime());
                response.setStorageDate(sdf.format(storageDate));

                // 计算到期日
                long expiryInMillis = (long) ingredient.getExpiryDays() * 24 * 60 * 60 * 1000;
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
                                 String category, String storageDate) throws Exception {
        // 更新食材分类
        Ingredient ingredient = ingredientRepository.findByName(ingredientName)
                .orElseThrow(() -> new RuntimeException("食材不存在"));
        ingredient.setMainCategory(category);
        ingredientRepository.save(ingredient);

        // 更新入库时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date = sdf.parse(storageDate);
        Timestamp timestamp = new Timestamp(date.getTime());

        userIngredientRepository.updateStorageTime(userId, ingredientName, timestamp);
    }
}
