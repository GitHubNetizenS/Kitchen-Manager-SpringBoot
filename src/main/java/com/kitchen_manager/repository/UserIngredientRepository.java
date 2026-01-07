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
public interface UserIngredientRepository extends JpaRepository<UserIngredient, Integer> {
    @Query("SELECT ui FROM UserIngredient ui WHERE ui.userId = :userId")
    List<UserIngredient> findByUserId(@Param("userId") Integer userId);

    @Modifying
    @Query("DELETE FROM UserIngredient ui WHERE ui.userId = :userId AND ui.ingredientId IN " +
            "(SELECT i.ingredientId FROM Ingredient i WHERE i.name = :ingredientName)")
    void deleteByUserIdAndIngredientName(@Param("userId") Integer userId,
                                         @Param("ingredientName") String ingredientName);

    @Modifying
    @Query("DELETE FROM UserIngredient ui WHERE ui.userId = :userId AND ui.ingredientId IN :ingredientIds")
    void deleteByUserIdAndIngredientIdIn(@Param("userId") Integer userId,
                                         @Param("ingredientIds") List<Integer> ingredientIds);

    @Modifying
    @Query("UPDATE UserIngredient ui SET ui.storageTime = :storageTime WHERE ui.userId = :userId " +
            "AND ui.ingredientId = (SELECT i.ingredientId FROM Ingredient i WHERE i.name = :ingredientName)")
    int updateStorageTime(@Param("userId") Integer userId,
                          @Param("ingredientName") String ingredientName,
                          @Param("storageTime") java.sql.Timestamp storageTime);

    void deleteByUserIdAndIngredientId(Integer userId, Integer ingredientId);
    // 新增：根据用户ID和食材ID查询
    @Query("SELECT ui FROM UserIngredient ui WHERE ui.userId = :userId AND ui.ingredientId = :ingredientId")
    Optional<UserIngredient> findByUserIdAndIngredientId(@Param("userId") Integer userId,
                                                         @Param("ingredientId") Integer ingredientId);

    List<Integer> findIngredientIdsByUserId(Integer userId);
}