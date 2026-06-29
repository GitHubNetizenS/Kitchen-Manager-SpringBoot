package com.kitchen_manager.service;

import com.kitchen_manager.entity.Ingredient;
import com.kitchen_manager.entity.Recipe;
import com.kitchen_manager.entity.UserIngredient;
import com.kitchen_manager.entity.UserShoppingList;
import com.kitchen_manager.repository.IngredientIdfRepository;
import com.kitchen_manager.repository.IngredientRepository;
import com.kitchen_manager.repository.RecipeIngredientRepository;
import com.kitchen_manager.repository.RecipeRepository;
import com.kitchen_manager.repository.RecipeTagRepository;
import com.kitchen_manager.repository.RecipeVideoRepository;
import com.kitchen_manager.repository.UserFavoriteRecipeRepository;
import com.kitchen_manager.repository.UserHistoryRepository;
import com.kitchen_manager.repository.UserIngredientRepository;
import com.kitchen_manager.repository.UserShoppingListRepository;
import com.kitchen_manager.repository.UserTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceShoppingCartTest {
    private static final Integer USER_ID = 1;
    private static final Integer RECIPE_ID = 10;
    private static final Integer OTHER_RECIPE_ID = 11;
    private static final Integer INGREDIENT_ID = 100;
    private static final Integer OTHER_INGREDIENT_ID = 101;
    private static final Integer EXISTING_CART_INGREDIENT_ID = 102;

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private UserFavoriteRecipeRepository favoriteRepository;
    @Mock
    private UserHistoryRepository historyRepository;
    @Mock
    private UserTagRepository userTagRepository;
    @Mock
    private RecipeTagRepository recipeTagRepository;
    @Mock
    private UserIngredientRepository userIngredientRepository;
    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;
    @Mock
    private IngredientIdfRepository ingredientIdfRepository;
    @Mock
    private UserShoppingListRepository shoppingListRepository;
    @Mock
    private RecipeVideoRepository recipeVideoRepository;
    @Mock
    private SearchService searchService;
    @Mock
    private UserHistoryRepository userHistoryRepository;
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private ElasticsearchSyncService elasticsearchSyncService;

    @InjectMocks
    private RecipeService recipeService;

    @Test
    void marksSameIngredientPurchasedAcrossUserCartAndKeepsCartRows() {
        when(shoppingListRepository.existsByUserIdAndRecipeIdAndIngredientId(
                USER_ID, RECIPE_ID, INGREDIENT_ID)).thenReturn(true);
        when(shoppingListRepository.updateIngredientStatusForUser(
                USER_ID, INGREDIENT_ID, "purchased")).thenReturn(2);
        when(userIngredientRepository.findByUserIdAndIngredientId(
                USER_ID, INGREDIENT_ID)).thenReturn(Optional.empty());

        recipeService.updateCartIngredientStatus(USER_ID, RECIPE_ID, INGREDIENT_ID, "purchased");

        verify(shoppingListRepository).updateIngredientStatusForUser(USER_ID, INGREDIENT_ID, "purchased");
        verify(shoppingListRepository, never()).deleteIngredientFromCart(anyInt(), anyInt(), anyInt());
        verify(userIngredientRepository).save(argThat(userIngredient ->
                USER_ID.equals(userIngredient.getUserId())
                        && INGREDIENT_ID.equals(userIngredient.getIngredientId())
                        && Integer.valueOf(1).equals(userIngredient.getQuantity())
                        && userIngredient.getStorageTime() != null));
    }

    @Test
    void allowsUncheckingAndMarksInventoryUnavailableWhenNoPurchasedCartItemsRemain() {
        UserIngredient existing = userIngredient(USER_ID, INGREDIENT_ID, 1);

        when(shoppingListRepository.existsByUserIdAndRecipeIdAndIngredientId(
                USER_ID, RECIPE_ID, INGREDIENT_ID)).thenReturn(true);
        when(shoppingListRepository.updateIngredientStatusForUser(
                USER_ID, INGREDIENT_ID, "pending")).thenReturn(2);
        when(shoppingListRepository.existsPurchasedIngredientForUser(
                USER_ID, INGREDIENT_ID)).thenReturn(false);
        when(userIngredientRepository.findByUserIdAndIngredientId(
                USER_ID, INGREDIENT_ID)).thenReturn(Optional.of(existing));

        recipeService.updateCartIngredientStatus(USER_ID, RECIPE_ID, INGREDIENT_ID, "pending");

        verify(shoppingListRepository).updateIngredientStatusForUser(USER_ID, INGREDIENT_ID, "pending");
        verify(shoppingListRepository, never()).deleteIngredientFromCart(anyInt(), anyInt(), anyInt());
        assertEquals(0, existing.getQuantity());
        verify(userIngredientRepository).save(existing);
    }

    @Test
    @SuppressWarnings("unchecked")
    void groupedShoppingCartCountsOnlyDisplayedCartItemsAndReturnsPurchasedRows() {
        Recipe recipe = recipe(RECIPE_ID, "Ice Fire Pineapple Bun");
        UserShoppingList pendingItem = cartItem(RECIPE_ID, INGREDIENT_ID, "pending");
        UserShoppingList purchasedItem = cartItem(RECIPE_ID, OTHER_INGREDIENT_ID, "purchased");

        when(shoppingListRepository.findDistinctRecipeIdsByUserId(USER_ID)).thenReturn(List.of(RECIPE_ID));
        when(recipeRepository.findById(RECIPE_ID)).thenReturn(Optional.of(recipe));
        when(shoppingListRepository.findByUserIdAndRecipeId(USER_ID, RECIPE_ID))
                .thenReturn(List.of(pendingItem, purchasedItem));
        when(ingredientRepository.findById(INGREDIENT_ID)).thenReturn(Optional.of(ingredient(INGREDIENT_ID, "milk")));
        when(ingredientRepository.findById(OTHER_INGREDIENT_ID))
                .thenReturn(Optional.of(ingredient(OTHER_INGREDIENT_ID, "cream")));
        when(shoppingListRepository.findLatestAddedTimeByUserIdAndRecipeId(USER_ID, RECIPE_ID))
                .thenReturn(new Timestamp(1000L));

        List<Map<String, Object>> result = recipeService.getGroupedShoppingCartByUserId(USER_ID);

        assertEquals(1, result.size());
        Map<String, Object> recipeMap = result.get(0);
        assertEquals(2, recipeMap.get("totalIngredients"));
        assertEquals(1, recipeMap.get("purchasedCount"));

        List<Map<String, Object>> ingredients = (List<Map<String, Object>>) recipeMap.get("ingredients");
        assertEquals(2, ingredients.size());
        assertEquals("pending", ingredients.get(0).get("status"));
        assertEquals("purchased", ingredients.get(1).get("status"));
    }

    @Test
    void batchStatusUpdateUsesSameIngredientSyncRuleForEachRecipeIngredient() {
        when(shoppingListRepository.findByUserIdAndRecipeId(USER_ID, RECIPE_ID))
                .thenReturn(List.of(
                        cartItem(RECIPE_ID, INGREDIENT_ID, "pending"),
                        cartItem(RECIPE_ID, OTHER_INGREDIENT_ID, "pending")
                ));
        when(shoppingListRepository.existsByUserIdAndRecipeIdAndIngredientId(
                USER_ID, RECIPE_ID, INGREDIENT_ID)).thenReturn(true);
        when(shoppingListRepository.existsByUserIdAndRecipeIdAndIngredientId(
                USER_ID, RECIPE_ID, OTHER_INGREDIENT_ID)).thenReturn(true);
        when(shoppingListRepository.updateIngredientStatusForUser(
                USER_ID, INGREDIENT_ID, "purchased")).thenReturn(2);
        when(shoppingListRepository.updateIngredientStatusForUser(
                USER_ID, OTHER_INGREDIENT_ID, "purchased")).thenReturn(1);
        when(userIngredientRepository.findByUserIdAndIngredientId(eq(USER_ID), anyInt()))
                .thenReturn(Optional.empty());

        recipeService.updateAllIngredientsStatus(USER_ID, RECIPE_ID, "PURCHASED");

        verify(shoppingListRepository).updateIngredientStatusForUser(USER_ID, INGREDIENT_ID, "purchased");
        verify(shoppingListRepository).updateIngredientStatusForUser(USER_ID, OTHER_INGREDIENT_ID, "purchased");
    }

    @Test
    void addToShoppingCartOnlyInsertsMissingIngredientsNotOwnedOrAlreadyInCart() {
        when(recipeIngredientRepository.findIngredientIdsByRecipeId(RECIPE_ID))
                .thenReturn(List.of(INGREDIENT_ID, OTHER_INGREDIENT_ID, EXISTING_CART_INGREDIENT_ID));
        when(userIngredientRepository.findByUserIdAndQuantity(USER_ID, 1))
                .thenReturn(List.of(userIngredient(USER_ID, OTHER_INGREDIENT_ID, 1)));
        when(shoppingListRepository.findByUserIdAndRecipeId(USER_ID, RECIPE_ID))
                .thenReturn(List.of(cartItem(RECIPE_ID, EXISTING_CART_INGREDIENT_ID, "pending")));

        recipeService.addToShoppingCart(USER_ID, RECIPE_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Iterable<UserShoppingList>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(shoppingListRepository).saveAll(captor.capture());

        List<UserShoppingList> savedItems = new ArrayList<>();
        captor.getValue().forEach(savedItems::add);

        assertEquals(1, savedItems.size());
        UserShoppingList saved = savedItems.get(0);
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(RECIPE_ID, saved.getRecipeId());
        assertEquals(INGREDIENT_ID, saved.getIngredientId());
        assertEquals("pending", saved.getStatus());
        assertNotNull(saved.getAddedTime());
    }

    private UserShoppingList cartItem(Integer recipeId, Integer ingredientId, String status) {
        UserShoppingList item = new UserShoppingList();
        item.setUserId(USER_ID);
        item.setRecipeId(recipeId);
        item.setIngredientId(ingredientId);
        item.setStatus(status);
        item.setAddedTime(new Timestamp(1000L + recipeId + ingredientId));
        return item;
    }

    private Recipe recipe(Integer recipeId, String name) {
        Recipe recipe = new Recipe();
        recipe.setRecipeId(recipeId);
        recipe.setName(name);
        recipe.setImageUrl("image-" + recipeId);
        return recipe;
    }

    private Ingredient ingredient(Integer ingredientId, String name) {
        Ingredient ingredient = new Ingredient();
        ingredient.setIngredientId(ingredientId);
        ingredient.setName(name);
        return ingredient;
    }

    private UserIngredient userIngredient(Integer userId, Integer ingredientId, Integer quantity) {
        UserIngredient userIngredient = new UserIngredient();
        userIngredient.setUserId(userId);
        userIngredient.setIngredientId(ingredientId);
        userIngredient.setQuantity(quantity);
        userIngredient.setStorageTime(new Timestamp(1000L));
        return userIngredient;
    }
}
