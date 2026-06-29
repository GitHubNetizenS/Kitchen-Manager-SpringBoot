package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.service.RecipeService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShoppingCartControllerTest {
    private static final Integer USER_ID = 1;
    private static final Integer RECIPE_ID = 10;
    private static final Integer INGREDIENT_ID = 100;

    private final RecipeService recipeService = mock(RecipeService.class);
    private final ShoppingCartController controller = new ShoppingCartController(recipeService);

    @Test
    void updateStatusReturnsRefreshedShoppingCartData() {
        List<Map<String, Object>> refreshedCart = List.of(
                Map.of(
                        "recipeId", RECIPE_ID,
                        "purchasedCount", 1,
                        "totalIngredients", 1,
                        "ingredients", List.of(Map.of(
                                "ingredientId", INGREDIENT_ID,
                                "status", "purchased"
                        ))
                ),
                Map.of(
                        "recipeId", 11,
                        "purchasedCount", 1,
                        "totalIngredients", 1,
                        "ingredients", List.of(Map.of(
                                "ingredientId", INGREDIENT_ID,
                                "status", "purchased"
                        ))
                )
        );
        when(recipeService.getGroupedShoppingCartByUserId(USER_ID)).thenReturn(refreshedCart);

        ApiResponse<List<Map<String, Object>>> response = controller.updateIngredientStatus(
                USER_ID, RECIPE_ID, INGREDIENT_ID, "PURCHASED");

        assertEquals(200, response.getCode());
        assertEquals(refreshedCart, response.getData());
        verify(recipeService).updateCartIngredientStatus(USER_ID, RECIPE_ID, INGREDIENT_ID, "purchased");
    }

    @Test
    void updateAllStatusReturnsRefreshedShoppingCartData() {
        List<Map<String, Object>> refreshedCart = List.of(
                Map.of(
                        "recipeId", RECIPE_ID,
                        "purchasedCount", 0,
                        "totalIngredients", 1,
                        "ingredients", List.of(Map.of(
                                "ingredientId", INGREDIENT_ID,
                                "status", "pending"
                        ))
                )
        );
        when(recipeService.getGroupedShoppingCartByUserId(USER_ID)).thenReturn(refreshedCart);

        ApiResponse<List<Map<String, Object>>> response = controller.updateAllIngredientsStatus(
                USER_ID, RECIPE_ID, "pending");

        assertEquals(200, response.getCode());
        assertEquals(refreshedCart, response.getData());
        verify(recipeService).updateAllIngredientsStatus(USER_ID, RECIPE_ID, "pending");
    }
}
