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

    // 新增：获取用户购物车的详细信息（包括菜谱和食材）
    @Query(value = "SELECT " +
            "   usl.recipe_id as recipeId, " +
            "   r.name as recipeName, " +
            "   r.image_url as imageUrl, " +
            "   usl.ingredient_id as ingredientId, " +
            "   i.name as ingredientName, " +
            "   usl.status as status " +
            "FROM user_shopping_list usl " +
            "JOIN recipe r ON usl.recipe_id = r.recipe_id " +
            "JOIN ingredient i ON usl.ingredient_id = i.ingredient_id " +
            "WHERE usl.user_id = :userId " +
            "ORDER BY usl.added_time DESC, usl.recipe_id, usl.ingredient_id",
            nativeQuery = true)
    List<Object[]> findShoppingCartDetailsByUserId(@Param("userId") Integer userId);

    // 更新购物车中食材的购买状态
    @Transactional
    @Modifying
    @Query("UPDATE UserShoppingList s SET s.status = :status " +
            "WHERE s.userId = :userId AND s.recipeId = :recipeId AND s.ingredientId = :ingredientId")
    void updateIngredientStatus(@Param("userId") Integer userId,
                                @Param("recipeId") Integer recipeId,
                                @Param("ingredientId") Integer ingredientId,
                                @Param("status") String status);

    // 删除购物车中的单个食材
    @Transactional
    @Modifying
    @Query("DELETE FROM UserShoppingList s " +
            "WHERE s.userId = :userId AND s.recipeId = :recipeId AND s.ingredientId = :ingredientId")
    void deleteIngredientFromCart(@Param("userId") Integer userId,
                                  @Param("recipeId") Integer recipeId,
                                  @Param("ingredientId") Integer ingredientId);

    // 按菜谱分组获取购物车数据
    @Query(value = "SELECT " +
            "   usl.recipe_id as recipeId, " +
            "   r.name as recipeName, " +
            "   r.image_url as imageUrl, " +
            "   GROUP_CONCAT(i.name SEPARATOR ', ') as ingredientList, " +
            "   COUNT(usl.ingredient_id) as totalIngredients, " +
            "   SUM(CASE WHEN usl.status = 'purchased' THEN 1 ELSE 0 END) as purchasedCount " +
            "FROM user_shopping_list usl " +
            "JOIN recipe r ON usl.recipe_id = r.recipe_id " +
            "JOIN ingredient i ON usl.ingredient_id = i.ingredient_id " +
            "WHERE usl.user_id = :userId " +
            "GROUP BY usl.recipe_id, r.name, r.image_url " +
            "ORDER BY MAX(usl.added_time) DESC",
            nativeQuery = true)
    static List<Object[]> findGroupedShoppingCartByUserId(@Param("userId") Integer userId) {
        return null;
    }

    // 获取用户购物车的详细信息（按菜谱分组）
    @Query(value = "SELECT " +
            "   usl.recipe_id as recipeId, " +
            "   r.name as recipeName, " +
            "   r.image_url as imageUrl, " +
            "   usl.ingredient_id as ingredientId, " +
            "   i.name as ingredientName, " +
            "   usl.status as status " +
            "FROM user_shopping_list usl " +
            "JOIN recipe r ON usl.recipe_id = r.recipe_id " +
            "JOIN ingredient i ON usl.ingredient_id = i.ingredient_id " +
            "WHERE usl.user_id = :userId " +
            "ORDER BY usl.added_time DESC, usl.recipe_id, usl.ingredient_id",
            nativeQuery = true)
    List<Object[]> findGroupedShoppingCartDetails(@Param("userId") Integer userId);
}