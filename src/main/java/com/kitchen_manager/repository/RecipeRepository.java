package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

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

    // 按标签分页查询（关联 recipe_tag 表）
    @Query(value = "SELECT r.* FROM recipe r " +
            "INNER JOIN recipe_tag rt ON r.recipe_id = rt.recipe_id " +
            "WHERE rt.tag_id = :tagId " +
            "ORDER BY r.popularity DESC " +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Recipe> findByTagIdOrderByPopularityWithPagination(@Param("tagId") Integer tagId,
                                                            @Param("offset") int offset,
                                                            @Param("limit") int limit);

    // 获取按标签查询的总数（用于分页计算总页数）
    @Query(value = "SELECT COUNT(r.recipe_id) FROM recipe r " +
            "INNER JOIN recipe_tag rt ON r.recipe_id = rt.recipe_id " +
            "WHERE rt.tag_id = :tagId",
            nativeQuery = true)
    long countByTagId(@Param("tagId") Integer tagId);

    // 全部菜谱分页查询
    @Query(value = "SELECT * FROM recipe ORDER BY popularity DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Recipe> findAllOrderByPopularityWithPagination(@Param("offset") int offset,
                                                        @Param("limit") int limit);

    // 获取全部菜谱总数
    @Query(value = "SELECT COUNT(recipe_id) FROM recipe", nativeQuery = true)
    long countAll();

    @Query("SELECT r, COALESCE(SUM(rt.matchAmount), 0) as totalMatch " +
            "FROM Recipe r " +
            "LEFT JOIN RecipeTag rt ON r.recipeId = rt.recipeId " +
            "LEFT JOIN UserTag ut ON rt.tagId = ut.tagId AND ut.userId = :userId " +
            "WHERE r.recipeId IN :recipeIds " +
            "GROUP BY r.recipeId " +
            "ORDER BY totalMatch DESC")
    List<Recipe> findByRecipeIdsOrderByMatchValue(@Param("recipeIds") List<Integer> recipeIds,
                                                  @Param("userId") Integer userId);

    /**
     * 增加菜谱热度
     */
    @Modifying
    @Query("UPDATE Recipe r SET r.popularity = r.popularity + 1 WHERE r.recipeId = :recipeId")
    void incrementPopularity(@Param("recipeId") Integer recipeId);

    /**
     * 增加菜谱热度（指定增加值）
     */
    @Modifying
    @Query("UPDATE Recipe r SET r.popularity = r.popularity + :amount WHERE r.recipeId = :recipeId")
    void increasePopularity(@Param("recipeId") Integer recipeId, @Param("amount") int amount);

    /**
     * 减少菜谱热度（确保热度不会小于0）
     */
    @Modifying
    @Query("UPDATE Recipe r SET r.popularity = CASE WHEN (r.popularity - :amount) < 0 THEN 0 ELSE (r.popularity - :amount) END WHERE r.recipeId = :recipeId")
    void decreasePopularity(@Param("recipeId") Integer recipeId, @Param("amount") int amount);

    // ============ 新增方法：支持收藏状态查询 ============

    /**
     * 查询所有菜谱（带收藏状态）
     * 返回 Map 以便包含额外的 isFavorite 字段
     */
    @Query(value = "SELECT " +
            "r.recipe_id, " +
            "r.name, " +
            "r.image_url, " +
            "r.taste, " +
            "r.method, " +
            "r.time, " +
            "r.difficulty, " +
            "r.needs, " +
            "r.steps, " +
            "r.popularity, " +
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite " +
            "FROM recipe r " +
            "LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "ORDER BY r.popularity DESC " +
            "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Map<String, Object>> findAllWithFavoriteStatus(@Param("userId") Integer userId,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);

    /**
     * 按标签查询菜谱（带收藏状态）
     * 返回 Map 以便包含额外的 isFavorite 字段
     */
    @Query(value = "SELECT " +
            "r.recipe_id, " +
            "r.name, " +
            "r.image_url, " +
            "r.taste, " +
            "r.method, " +
            "r.time, " +
            "r.difficulty, " +
            "r.needs, " +
            "r.steps, " +
            "r.popularity, " +
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite " +
            "FROM recipe r " +
            "INNER JOIN recipe_tag rt ON r.recipe_id = rt.recipe_id " +
            "LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "WHERE rt.tag_id = :tagId " +
            "ORDER BY r.popularity DESC " +
            "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Map<String, Object>> findByTagIdWithFavoriteStatus(@Param("tagId") Integer tagId,
                                                            @Param("userId") Integer userId,
                                                            @Param("offset") int offset,
                                                            @Param("limit") int limit);

    /**
     * 根据菜谱ID查询（带收藏状态）- 用于单个菜谱查询
     */
    @Query(value = "SELECT " +
            "r.recipe_id, " +
            "r.name, " +
            "r.image_url, " +
            "r.taste, " +
            "r.method, " +
            "r.time, " +
            "r.difficulty, " +
            "r.needs, " +
            "r.steps, " +
            "r.popularity, " +
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite " +
            "FROM recipe r " +
            "LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "WHERE r.recipe_id = :recipeId", nativeQuery = true)
    Map<String, Object> findByIdWithFavoriteStatus(@Param("recipeId") Integer recipeId,
                                                   @Param("userId") Integer userId);

    /**
     * 查询用户收藏的菜谱ID列表
     */
    @Query(value = "SELECT recipe_id FROM userfavoriterecipe WHERE user_id = :userId", nativeQuery = true)
    List<Integer> findFavoriteRecipeIdsByUserId(@Param("userId") Integer userId);
}