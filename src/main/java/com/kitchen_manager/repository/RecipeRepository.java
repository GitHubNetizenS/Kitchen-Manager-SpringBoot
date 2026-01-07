package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

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

    /**
     * 获取菜谱候选集
     * @param offset 偏移值
     * @param limit 限制值
     * @return 菜谱候选集列表
     */
    @Query(value =
            "SELECT * " +
            "FROM recipe " +
            "LIMIT :limit " +
            "OFFSET :offset",
            nativeQuery = true)
    List<Recipe> findAllForCandidateSet(@Param("offset") int offset,
                                        @Param("limit") int limit);

    /**
     * 按标签ID获取菜谱候选集
     * 只返回在当前tagId（1/2/3）对应的match_amount是三个核心标签（1/2/3）中最大值的菜谱
     * @param tagId 标签ID（只能是1、2、3）
     * @param offset 偏移值
     * @param limit 限制值
     * @return 菜谱候选集列表
     */
    @Query(value =
            "SELECT r.* " +
                    "FROM recipe r " +
                    "WHERE r.recipe_id IN ( " +
                    "   SELECT rt.recipe_id " +
                    "   FROM recipe_tag rt " +
                    "   WHERE rt.tag_id IN (1, 2, 3) " +  // 只考虑1、2、3三个标签
                    "   GROUP BY rt.recipe_id " +
                    "   HAVING MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
                    "          MAX(IF(rt.tag_id = 1, rt.match_amount, -1)) " +
                    "      AND MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
                    "          MAX(IF(rt.tag_id = 2, rt.match_amount, -1)) " +
                    "      AND MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
                    "          MAX(IF(rt.tag_id = 3, rt.match_amount, -1)) " +
                    ") " +
                    "ORDER BY r.popularity DESC " +
                    "LIMIT :limit " +
                    "OFFSET :offset",
            nativeQuery = true)
    List<Recipe> findByTagIdForCandidateSet(@Param("tagId") Integer tagId,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

}