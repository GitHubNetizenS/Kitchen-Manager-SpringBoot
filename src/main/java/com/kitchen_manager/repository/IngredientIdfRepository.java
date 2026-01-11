package com.kitchen_manager.repository;

import com.kitchen_manager.entity.IngredientIdf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 原料IDF表的数据库操作类
 */
@Repository
public interface IngredientIdfRepository extends JpaRepository<IngredientIdf, Integer> {
    @Query( "SELECT i " +
            "FROM IngredientIdf i")
    List<IngredientIdf> findAllIdf();
}