package com.kitchen_manager.repository;

import com.kitchen_manager.entity.IngredientIdf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 原料IDF表的数据库操作类
 */
@Repository
public interface IngredientIdfRepository extends JpaRepository<IngredientIdf, Integer> {

}