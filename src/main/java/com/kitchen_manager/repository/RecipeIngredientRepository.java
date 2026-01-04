package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Integer> {
    @Query("SELECT ri.ingredientId FROM RecipeIngredient ri WHERE ri.recipeId = :recipeId")
    List<Integer> findIngredientIdsByRecipeId(@Param("recipeId") Integer recipeId);

    @Query("SELECT DISTINCT ri.recipeId FROM RecipeIngredient ri WHERE ri.ingredientId IN :ingredientIds")
    List<Integer> findRecipeIdsByIngredientIds(@Param("ingredientIds") List<Integer> ingredientIds);
}
