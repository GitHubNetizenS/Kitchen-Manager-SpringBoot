package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Integer> {
    @Query("SELECT r FROM Recipe r ORDER BY r.popularity DESC")
    List<Recipe> findAllOrderByPopularity();

    @Query("SELECT r FROM Recipe r WHERE r.name LIKE %:keyword%")
    List<Recipe> searchByName(@Param("keyword") String keyword);

    @Query("SELECT DISTINCT r FROM Recipe r " +
            "LEFT JOIN RecipeIngredient ri ON r.recipeId = ri.recipeId " +
            "LEFT JOIN Ingredient i ON ri.ingredientId = i.ingredientId " +
            "WHERE r.name LIKE %:keyword% OR i.name LIKE %:keyword%")
    List<Recipe> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT r, COALESCE(SUM(rt.matchAmount), 0) as totalMatch " +
            "FROM Recipe r " +
            "LEFT JOIN RecipeTag rt ON r.recipeId = rt.recipeId " +
            "LEFT JOIN UserTag ut ON rt.tagId = ut.tagId AND ut.userId = :userId " +
            "WHERE r.recipeId IN :recipeIds " +
            "GROUP BY r.recipeId " +
            "ORDER BY totalMatch DESC")
    List<Recipe> findByRecipeIdsOrderByMatchValue(@Param("recipeIds") List<Integer> recipeIds,
                                                  @Param("userId") Integer userId);
}
