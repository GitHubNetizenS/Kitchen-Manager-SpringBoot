package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Integer> {
    Optional<Ingredient> findByName(String name);
    List<Ingredient> findByNameContaining(String keyword);

    @Query("SELECT i FROM Ingredient i WHERE i.ingredientId IN :ids")
    List<Ingredient> findByIds(@Param("ids") List<Integer> ids);
}
