package com.kitchen_manager.config;

import com.kitchen_manager.service.IngredientIdfCalculator;
import lombok.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ingredient_idf表初始化启动类
 */
@Component
public class IngredientIdfStartupRunner implements ApplicationRunner {
    private final IngredientIdfCalculator ingredientIdfCalculator;

    public IngredientIdfStartupRunner(IngredientIdfCalculator ingredientIdfCalculator) {
        this.ingredientIdfCalculator = ingredientIdfCalculator;
    }

    /**
     * SpringBoot启动完成后自动调用该方法
     * @param args 调用参数
     */
    @Override
    public void run(@NonNull ApplicationArguments args) {
        ingredientIdfCalculator.calculateAndStoreIdf();
    }
}