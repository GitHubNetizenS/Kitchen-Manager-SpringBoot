package com.kitchen_manager.repository;

import com.kitchen_manager.entity.RecipeTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RecipeTagRepository extends JpaRepository<RecipeTag, Integer> {

    @Query(value = "SELECT * " +
           "FROM recipe_tag " +
           "WHERE recipe_id=:recipeId", nativeQuery = true)
    List<RecipeTag> findByRecipeId(Integer recipeId);

    List<RecipeTag> findByRecipeIdIn(List<Integer> recipeIds);
}