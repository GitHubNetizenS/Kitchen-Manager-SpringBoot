package com.kitchen_manager.repository;

import com.kitchen_manager.entity.UserShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserShoppingListRepository extends JpaRepository<UserShoppingList, Integer> {

    // 检查用户是否已将菜谱加入购物车
    @Query("SELECT COUNT(s) > 0 FROM UserShoppingList s WHERE s.userId = :userId AND s.recipeId = :recipeId")
    boolean existsByUserIdAndRecipeId(@Param("userId") Integer userId, @Param("recipeId") Integer recipeId);

    // 获取用户购物车中的所有菜谱ID
    @Query("SELECT DISTINCT s.recipeId FROM UserShoppingList s WHERE s.userId = :userId")
    List<Integer> findRecipeIdsByUserId(@Param("userId") Integer userId);

    // 删除购物车中的菜谱
    @Transactional
    @Modifying
    @Query("DELETE FROM UserShoppingList s WHERE s.userId = :userId AND s.recipeId = :recipeId")
    void deleteByUserIdAndRecipeId(@Param("userId") Integer userId, @Param("recipeId") Integer recipeId);

    // 将菜谱所需食材加入购物车
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO user_shopping_list (user_id, recipe_id, ingredient_id, status, added_time) " +
            "SELECT :userId, ri.recipe_id, ri.ingredient_id, 'PENDING', CURRENT_TIMESTAMP " +
            "FROM recipeingredient ri WHERE ri.recipe_id = :recipeId",
            nativeQuery = true)
    int addRecipeIngredientsToCart(@Param("userId") Integer userId, @Param("recipeId") Integer recipeId);

    // 检查用户是否已添加某个菜谱的食材到购物车
    @Query(value = "SELECT COUNT(*) > 0 FROM user_shopping_list " +
            "WHERE user_id = :userId AND recipe_id = :recipeId LIMIT 1",
            nativeQuery = true)
    boolean hasRecipeInCart(@Param("userId") Integer userId, @Param("recipeId") Integer recipeId);
}