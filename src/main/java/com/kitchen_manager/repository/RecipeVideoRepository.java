package com.kitchen_manager.repository;

import com.kitchen_manager.entity.RecipeVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipeVideoRepository extends JpaRepository<RecipeVideo, Integer> {

    /**
     * 根据菜谱ID查询视频列表
     */
    List<RecipeVideo> findByRecipeId(Integer recipeId);

    /**
     * 根据菜谱ID和平台查询视频
     */
    Optional<RecipeVideo> findByRecipeIdAndPlatform(Integer recipeId, String platform);

    /**
     * 查询某个菜谱是否有视频
     */
    boolean existsByRecipeId(Integer recipeId);
}