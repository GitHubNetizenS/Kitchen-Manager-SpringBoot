-- 步骤1：连接到你的MySQL数据库（这里假设你已经连接）
-- 使用 kitchen-manager 数据库
USE `kitchen-manager`;

-- 步骤2：将recipe表中的popularity列清零
UPDATE recipe 
SET popularity = 0;

-- 步骤3：根据userfavoriterecipe表更新popularity（每个收藏+10）
UPDATE recipe r
LEFT JOIN (
    SELECT recipe_id, COUNT(*) as favorite_count
    FROM userfavoriterecipe
    GROUP BY recipe_id
) f ON r.recipe_id = f.recipe_id
SET r.popularity = r.popularity + COALESCE(f.favorite_count, 0) * 10;

-- 步骤4：根据user_history表更新popularity（每个烹饪记录+20）
UPDATE recipe r
LEFT JOIN (
    SELECT recipe_id, COUNT(*) as cook_count
    FROM user_history
    GROUP BY recipe_id
) h ON r.recipe_id = h.recipe_id
SET r.popularity = r.popularity + COALESCE(h.cook_count, 0) * 20;

-- 步骤5：为每个菜谱添加1-10的随机整数（模拟浏览记录）
UPDATE recipe 
SET popularity = popularity + FLOOR(1 + (RAND() * 10));

-- 步骤6：验证结果（可选）
SELECT recipe_id, popularity 
FROM recipe 
ORDER BY popularity DESC 
LIMIT 10;