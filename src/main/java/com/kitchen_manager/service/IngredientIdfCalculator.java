package com.kitchen_manager.service;

import com.kitchen_manager.entity.IngredientIdf;
import com.kitchen_manager.repository.IngredientIdfRepository;
import com.kitchen_manager.repository.RecipeIngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngredientIdfCalculator {

    private final RecipeIngredientRepository recipeIngredientRepository;
    private final IngredientIdfRepository ingredientIdfRepository;

    public IngredientIdfCalculator(RecipeIngredientRepository recipeIngredientRepository,
                                   IngredientIdfRepository ingredientIdfRepository) {
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.ingredientIdfRepository = ingredientIdfRepository;
    }

    @Transactional
    public void calculateAndStoreIdf() {
        long totalRecipes = recipeIngredientRepository.countTotalRecipes();

        if(0==totalRecipes) {

            return;
        }

        List<Object[]>      stats = recipeIngredientRepository.countRecipeByIngredient();
        List<IngredientIdf> idfList = new ArrayList<>(stats.size());

        for(Object[] row: stats) {
            Integer ingredientId = (Integer)row[0]; // 原料ID
            Long    recipeCount = (Long)row[1];     // 包含该原料的菜谱数（DF）
            // IDF = log(总文档数/(包含该词的文档数+1))
            // IDF越大，表示越独特。
            double  idf = Math.log((double)totalRecipes / (1.0+recipeCount));

            idfList.add(new IngredientIdf(ingredientId, idf));
        }

        ingredientIdfRepository.deleteAll();
        ingredientIdfRepository.saveAll(idfList);
    }
}
