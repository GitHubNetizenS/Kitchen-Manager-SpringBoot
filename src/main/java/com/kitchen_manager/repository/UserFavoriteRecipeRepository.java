package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserFavoriteRecipeRepository extends JpaRepository<UserFavoriteRecipe, Integer> {
    long countByUserId(Integer userId);
    boolean existsByUserIdAndRecipeId(Integer userId, Integer recipeId);

    @Query("SELECT ufr.recipeId FROM UserFavoriteRecipe ufr WHERE ufr.userId = :userId")
    List<Integer> findRecipeIdsByUserId(@Param("userId") Integer userId);

    void deleteByUserIdAndRecipeId(Integer userId, Integer recipeId);
}