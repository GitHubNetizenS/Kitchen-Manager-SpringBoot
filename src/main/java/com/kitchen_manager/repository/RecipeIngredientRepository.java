package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Integer> {
    @Query("SELECT ri.ingredientId FROM RecipeIngredient ri WHERE ri.recipeId = :recipeId")
    List<Integer> findIngredientIdsByRecipeId(@Param("recipeId") Integer recipeId);

    @Query("SELECT DISTINCT ri.recipeId FROM RecipeIngredient ri WHERE ri.ingredientId IN :ingredientIds")
    List<Integer> findRecipeIdsByIngredientIds(@Param("ingredientIds") List<Integer> ingredientIds);

    /**
     * 通过菜谱ID查找菜谱与原料的关系
     * @param recipeId 菜谱ID
     * @return 符合条件的菜谱-原料关系
     */
    @Query( "SELECT ri " +
            "FROM RecipeIngredient ri " +
            "WHERE ri.recipeId = :recipeId")
    List<RecipeIngredient> findByRecipeId(@Param("recipeId") Integer recipeId);

    /**
     * 统计某一recipeId出现的次数（有几个原料）
     * @return 出现次数
     */
    @Query( "SELECT COUNT(DISTINCT ri.recipeId) " +
            "FROM RecipeIngredient ri")
    long countTotalRecipes();

    /**
     * 统计每个原料出现在多少个菜谱中
     * @return 每一个原料出现的次数，例如：[原料1, 150]，[原料2, 80]，[原料3, 20]……
     */
    @Query( "SELECT ri.ingredientId, COUNT(DISTINCT ri.recipeId) " +
            "FROM RecipeIngredient ri " +
            "GROUP BY ri.ingredientId")
    List<Object[]> countRecipeByIngredient();

    List<RecipeIngredient> findByRecipeIdIn(List<Integer> recipeIds);
}