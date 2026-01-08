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

    /**
     * 获取菜谱候选集（带收藏状态和购物车状态）
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
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite, " +
            "CASE WHEN usl.id IS NOT NULL THEN 1 ELSE 0 END as inShoppingCart " +
            "FROM recipe r " +
            "LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "LEFT JOIN user_shopping_list usl ON r.recipe_id = usl.recipe_id AND usl.user_id = :userId " +
            "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Map<String, Object>> findAllForCandidateSetWithFavoriteStatus(@Param("userId") Integer userId,
                                                                       @Param("offset") int offset,
                                                                       @Param("limit") int limit);

    /**
     * 按标签ID获取菜谱候选集（带收藏状态和购物车状态）
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
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite, " +
            "CASE WHEN usl.id IS NOT NULL THEN 1 ELSE 0 END as inShoppingCart " +
            "FROM recipe r " +
            "LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "LEFT JOIN user_shopping_list usl ON r.recipe_id = usl.recipe_id AND usl.user_id = :userId " +
            "WHERE r.recipe_id IN ( " +
            "   SELECT rt.recipe_id " +
            "   FROM recipe_tag rt " +
            "   WHERE rt.tag_id IN (1, 2, 3) " +
            "   GROUP BY rt.recipe_id " +
            "   HAVING MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
            "          MAX(IF(rt.tag_id = 1, rt.match_amount, -1)) " +
            "      AND MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
            "          MAX(IF(rt.tag_id = 2, rt.match_amount, -1)) " +
            "      AND MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
            "          MAX(IF(rt.tag_id = 3, rt.match_amount, -1)) " +
            ") " +
            "ORDER BY r.popularity DESC " +
            "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Map<String, Object>> findByTagIdForCandidateSetWithFavoriteStatus(@Param("tagId") Integer tagId,
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

    /**
     * 口味筛选 - 适配现有数据库环境
     * 使用简单的LIKE查询，确保兼容性
     */
    @Query(value = "SELECT r.recipe_id FROM recipe r " +
            "WHERE (:taste = '' OR " +  // 如果口味为空，不进行筛选
            " (CASE " +
            // 处理多选口味（如"酸,甜"）
            "   WHEN :taste LIKE '%,%' THEN " +
            "     (r.taste LIKE CONCAT('%', TRIM(SUBSTRING_INDEX(:taste, ',', 1)), '%') " +
            "      AND r.taste LIKE CONCAT('%', TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(:taste, ',', 2), ',', -1)), '%') " +
            "      AND CASE WHEN CHAR_LENGTH(:taste) - CHAR_LENGTH(REPLACE(:taste, ',', '')) >= 2 " +
            "                THEN r.taste LIKE CONCAT('%', TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(:taste, ',', 3), ',', -1)), '%') " +
            "                ELSE 1=1 END " +
            "     ) " +
            // 处理单选口味（如"酸"）
            "   ELSE r.taste LIKE CONCAT('%', :taste, '%') " +
            " END)) " +
            "AND (:method = '' OR r.method = :method) " +
            "AND (:difficulty = '' OR r.difficulty = :difficulty) " +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Integer> findFilteredRecipeIds(
            @Param("taste") String taste,
            @Param("method") String method,
            @Param("difficulty") String difficulty,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 获取总数的方法 - 对应上面的筛选逻辑
     */
    @Query(value = "SELECT COUNT(r.recipe_id) FROM recipe r " +
            "WHERE (:taste = '' OR " +
            " (CASE " +
            "   WHEN :taste LIKE '%,%' THEN " +
            "     (r.taste LIKE CONCAT('%', TRIM(SUBSTRING_INDEX(:taste, ',', 1)), '%') " +
            "      AND r.taste LIKE CONCAT('%', TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(:taste, ',', 2), ',', -1)), '%') " +
            "      AND CASE WHEN CHAR_LENGTH(:taste) - CHAR_LENGTH(REPLACE(:taste, ',', '')) >= 2 " +
            "                THEN r.taste LIKE CONCAT('%', TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(:taste, ',', 3), ',', -1)), '%') " +
            "                ELSE 1=1 END " +
            "     ) " +
            "   ELSE r.taste LIKE CONCAT('%', :taste, '%') " +
            " END)) " +
            "AND (:method = '' OR r.method = :method) " +
            "AND (:difficulty = '' OR r.difficulty = :difficulty)",
            nativeQuery = true)
    long countFilteredRecipes(
            @Param("taste") String taste,
            @Param("method") String method,
            @Param("difficulty") String difficulty);

    // 备用方案：使用存储过程调用的方式（如果上面的方法不工作）
    /**
     * 备用方案：使用更简单的逻辑处理多选
     * 最多支持2个口味的多选
     */
    @Query(value = "SELECT r.recipe_id FROM recipe r " +
            "WHERE 1=1 " +
            "AND CASE WHEN :taste = '' THEN 1=1 " +
            "     WHEN :taste NOT LIKE '%,%' THEN r.taste LIKE CONCAT('%', :taste, '%') " +
            // 处理两个口味的情况
            "     ELSE (r.taste LIKE CONCAT('%', SUBSTRING_INDEX(:taste, ',', 1), '%') " +
            "           AND r.taste LIKE CONCAT('%', SUBSTRING_INDEX(:taste, ',', -1), '%')) " +
            "     END " +
            "AND (:method = '' OR r.method = :method) " +
            "AND (:difficulty = '' OR r.difficulty = :difficulty) " +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Integer> findFilteredRecipeIdsSimple(
            @Param("taste") String taste,
            @Param("method") String method,
            @Param("difficulty") String difficulty,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 备用方案的总数查询
     */
    @Query(value = "SELECT COUNT(r.recipe_id) FROM recipe r " +
            "WHERE 1=1 " +
            "AND CASE WHEN :taste = '' THEN 1=1 " +
            "     WHEN :taste NOT LIKE '%,%' THEN r.taste LIKE CONCAT('%', :taste, '%') " +
            "     ELSE (r.taste LIKE CONCAT('%', SUBSTRING_INDEX(:taste, ',', 1), '%') " +
            "           AND r.taste LIKE CONCAT('%', SUBSTRING_INDEX(:taste, ',', -1), '%')) " +
            "     END " +
            "AND (:method = '' OR r.method = :method) " +
            "AND (:difficulty = '' OR r.difficulty = :difficulty)",
            nativeQuery = true)
    long countFilteredRecipesSimple(
            @Param("taste") String taste,
            @Param("method") String method,
            @Param("difficulty") String difficulty);


    /**
     * 带收藏状态的批量查询
     */
    @Query(value = "SELECT r.recipe_id, r.name, r.image_url, r.taste, r.method, r.time, r.difficulty, r.needs, r.steps, r.popularity, " +
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite " +
            "FROM recipe r LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "WHERE r.recipe_id IN :recipeIds", nativeQuery = true)
    List<Map<String, Object>> findByIdsWithFavoriteStatus(@Param("recipeIds") List<Integer> recipeIds, @Param("userId") Integer userId);

    /**
     * 按popularity排序（用于综合）
     */
    @Query("SELECT r FROM Recipe r WHERE r.recipeId IN :recipeIds ORDER BY r.popularity DESC")
    List<Recipe> findByIdsOrderByPopularity(@Param("recipeIds") List<Integer> recipeIds);

    /**
     * 按复合口味筛选的备用方案（使用REGEXP正则表达式，MySQL 8.0+）
     * 如果LOCATE不工作，可以尝试这个
     */
    @Query(value = "SELECT r.recipe_id FROM recipe r " +
            "WHERE (:taste = '' OR " +
            " (CASE " +
            // 处理多选口味
            "   WHEN :taste LIKE '%,%' THEN " +
            "     ( " +
            "       r.taste REGEXP CONCAT('.*', SUBSTRING_INDEX(:taste, ',', 1), '.*') " +
            "       OR " +
            "       r.taste REGEXP CONCAT('.*', SUBSTRING_INDEX(SUBSTRING_INDEX(:taste, ',', 2), ',', -1), '.*') " +
            "     ) " +
            // 处理单个口味
            "   ELSE r.taste REGEXP CONCAT('.*', :taste, '.*') " +
            " END)) " +
            "AND (:method = '' OR r.method = :method) " +
            "AND (:difficulty = '' OR r.difficulty = :difficulty) " +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Integer> findFilteredRecipeIdsByRegex(
            @Param("taste") String taste,
            @Param("method") String method,
            @Param("difficulty") String difficulty,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 扩展口味匹配范围（解决"咸"匹配不到"咸香"的问题）
     * 使用自定义函数扩展口味匹配
     */
    @Query(value = "SELECT r.recipe_id FROM recipe r " +
            "WHERE (:taste = '' OR " +
            " (CASE " +
            // 处理多选口味
            "   WHEN :taste LIKE '%,%' THEN " +
            "     ( " +
            "       (r.taste LIKE CONCAT('%', SUBSTRING_INDEX(:taste, ',', 1), '%')) " +
            "       OR " +
            "       (r.taste LIKE CONCAT('%', SUBSTRING_INDEX(SUBSTRING_INDEX(:taste, ',', 2), ',', -1), '%')) " +
            // 扩展口味匹配：如果选择"咸"，也匹配"咸香"、"咸鲜"等
            "       OR (:taste LIKE '%咸%' AND (r.taste LIKE '%咸香%' OR r.taste LIKE '%咸鲜%' OR r.taste LIKE '%咸辣%')) " +
            "       OR (:taste LIKE '%酸%' AND (r.taste LIKE '%酸甜%' OR r.taste LIKE '%酸辣%' OR r.taste LIKE '%酸爽%')) " +
            "       OR (:taste LIKE '%甜%' AND (r.taste LIKE '%香甜%' OR r.taste LIKE '%酸甜%' OR r.taste LIKE '%甜咸%')) " +
            "       OR (:taste LIKE '%辣%' AND (r.taste LIKE '%麻辣%' OR r.taste LIKE '%香辣%' OR r.taste LIKE '%酸辣%')) " +
            "     ) " +
            // 处理单个口味
            "   ELSE (r.taste LIKE CONCAT('%', :taste, '%') " +
            // 扩展单个口味的匹配范围
            "         OR (:taste = '咸' AND (r.taste LIKE '%咸香%' OR r.taste LIKE '%咸鲜%')) " +
            "         OR (:taste = '酸' AND (r.taste LIKE '%酸甜%' OR r.taste LIKE '%酸辣%')) " +
            "         OR (:taste = '甜' AND (r.taste LIKE '%香甜%' OR r.taste LIKE '%酸甜%')) " +
            "         OR (:taste = '辣' AND (r.taste LIKE '%麻辣%' OR r.taste LIKE '%香辣%'))) " +
            " END)) " +
            "AND (:method = '' OR r.method = :method) " +
            "AND (:difficulty = '' OR r.difficulty = :difficulty) " +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Integer> findFilteredRecipeIdsExtended(
            @Param("taste") String taste,
            @Param("method") String method,
            @Param("difficulty") String difficulty,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 动态口味筛选方法 - 更灵活，支持任意数量的口味
     * 使用字符串构建动态SQL
     */
    @Query(value = "SELECT r.recipe_id FROM recipe r WHERE 1=1 " +
            "AND (:tasteConditions = '' OR :tasteConditions = '1=1') " +
            "AND (:method = '' OR r.method = :method) " +
            "AND (:difficulty = '' OR r.difficulty = :difficulty) " +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Integer> findFilteredRecipeIdsDynamic(
            @Param("tasteConditions") String tasteConditions,
            @Param("method") String method,
            @Param("difficulty") String difficulty,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 动态总数查询
     */
    @Query(value = "SELECT COUNT(r.recipe_id) FROM recipe r WHERE 1=1 " +
            "AND (:tasteConditions = '' OR :tasteConditions = '1=1') " +
            "AND (:method = '' OR r.method = :method) " +
            "AND (:difficulty = '' OR r.difficulty = :difficulty)",
            nativeQuery = true)
    long countFilteredRecipesDynamic(
            @Param("tasteConditions") String tasteConditions,
            @Param("method") String method,
            @Param("difficulty") String difficulty);

    /**
     * 查询所有菜谱（带收藏状态和购物车状态）- 用于全部标签
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
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite, " +
            "CASE WHEN usl.id IS NOT NULL THEN 1 ELSE 0 END as inShoppingCart " +
            "FROM recipe r " +
            "LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "LEFT JOIN user_shopping_list usl ON r.recipe_id = usl.recipe_id AND usl.user_id = :userId " +
            "ORDER BY r.popularity DESC " +
            "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Map<String, Object>> findAllWithFavoriteAndCartStatus(
            @Param("userId") Integer userId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 按标签查询菜谱（带收藏状态和购物车状态）- 用于推荐列表
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
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite, " +
            "CASE WHEN usl.id IS NOT NULL THEN 1 ELSE 0 END as inShoppingCart " +
            "FROM recipe r " +
            "INNER JOIN recipe_tag rt ON r.recipe_id = rt.recipe_id " +
            "LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "LEFT JOIN user_shopping_list usl ON r.recipe_id = usl.recipe_id AND usl.user_id = :userId " +
            "WHERE rt.tag_id = :tagId " +
            "ORDER BY r.popularity DESC " +
            "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Map<String, Object>> findByTagIdWithFavoriteAndCartStatus(
            @Param("tagId") Integer tagId,
            @Param("userId") Integer userId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 按标签获取菜谱候选集（带收藏状态和购物车状态）- 用于分页推荐
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
            "CASE WHEN ufr.id IS NOT NULL THEN 1 ELSE 0 END as isFavorite, " +
            "CASE WHEN usl.id IS NOT NULL THEN 1 ELSE 0 END as inShoppingCart " +
            "FROM recipe r " +
            "LEFT JOIN userfavoriterecipe ufr ON r.recipe_id = ufr.recipe_id AND ufr.user_id = :userId " +
            "LEFT JOIN user_shopping_list usl ON r.recipe_id = usl.recipe_id AND usl.user_id = :userId " +
            "WHERE r.recipe_id IN ( " +
            "   SELECT rt.recipe_id " +
            "   FROM recipe_tag rt " +
            "   WHERE rt.tag_id IN (1, 2, 3) " +
            "   GROUP BY rt.recipe_id " +
            "   HAVING MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
            "          MAX(IF(rt.tag_id = 1, rt.match_amount, -1)) " +
            "      AND MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
            "          MAX(IF(rt.tag_id = 2, rt.match_amount, -1)) " +
            "      AND MAX(CASE WHEN rt.tag_id = :tagId THEN rt.match_amount END) >= " +
            "          MAX(IF(rt.tag_id = 3, rt.match_amount, -1)) " +
            ") " +
            "ORDER BY r.popularity DESC " +
            "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Map<String, Object>> findByTagIdForCandidateSetWithFavoriteAndCartStatus(
            @Param("tagId") Integer tagId,
            @Param("userId") Integer userId,
            @Param("offset") int offset,
            @Param("limit") int limit);
}