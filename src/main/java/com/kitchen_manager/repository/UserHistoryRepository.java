package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserHistoryRepository extends JpaRepository<UserHistory, Integer> {
    long countByUserId(Integer userId);
    List<UserHistory> findByUserId(Integer userId);

    @Query("SELECT uh.recipeId FROM UserHistory uh WHERE uh.userId = :userId")
    List<Integer> findRecipeIdsByUserId(@Param("userId") Integer userId);

    @Query("SELECT h FROM UserHistory h WHERE h.userId = :userId ORDER BY h.cookTime DESC")
    List<UserHistory> findByUserIdOrderByCookTimeDesc(@Param("userId") Integer userId);
}
