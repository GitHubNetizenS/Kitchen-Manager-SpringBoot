import math
import random
import numpy as np
import pandas as pd
from sqlalchemy import create_engine
from session_encoder import SessionEncoder
# 数据库连接到kitchen-manager。
conn = create_engine(
    "mysql+pymysql://root:123456@localhost:3306/kitchen-manager?charset=utf8mb4"
)
NEG_SAMPLE_RATIO = 15  # 负样本比例（相对正样本）。
import json
with open("gru4rec_config.json", "r") as f:
    _config = json.load(f)
session_encoder = SessionEncoder("gru4rec_model.pth", num_items=_config["num_items"])
# 匹配度计算。
def calculate_tag_match(user_tags, recipe_tags, tag_dim=13):
    user_vec = [0.0] * tag_dim
    recipe_vec = [0.0] * tag_dim

    for t in user_tags:
        user_vec[t - 1] = 1.0
    for t, amount in recipe_tags.items():
        recipe_vec[t - 1] = amount

    dot = sum(u * r for u, r in zip(user_vec, recipe_vec))
    norm_user = math.sqrt(sum(u * u for u in user_vec))
    norm_recipe = math.sqrt(sum(r * r for r in recipe_vec))

    if 0==norm_user or 0==norm_recipe:

        return 0.0

    return dot / (norm_user * norm_recipe)

def calculate_ingredient_match(user_ingredients, recipe_ingredients, ingredient_idf):
    if not recipe_ingredients:

        return -1.0

    match_count = len(user_ingredients & recipe_ingredients)
    coverage = match_count / len(recipe_ingredients)

    if coverage < 0.15:

        return 0.01*coverage

    tf = 1.0 / len(recipe_ingredients)
    missing_idf_sum = 0.0
    total_idf_sum = 0.0

    for ing in recipe_ingredients:
        idf = ingredient_idf.get(ing, 1.0)

        total_idf_sum += idf * tf
        if ing not in user_ingredients:
            missing_idf_sum += idf * tf

    missing_core_ratio = missing_idf_sum / total_idf_sum if total_idf_sum>0 else 1.0

    return coverage*(1.0-missing_core_ratio)
# 读取数据。
recipes_df = pd.read_sql(
    "SELECT recipe_id, popularity "
    "FROM recipe", conn)
recipe_ids = recipes_df.recipe_id.tolist()
recipe_tags_df = pd.read_sql(
    "SELECT recipe_id, tag_id, match_amount "
    "FROM recipe_tag", conn)
user_tags_df = pd.read_sql(
    "SELECT user_id, tag_id "
    "FROM usertag", conn)
recipe_ingredients_df = pd.read_sql(
    "SELECT recipe_id, ingredient_id "
    "FROM recipeingredient", conn)
user_ingredients_df = pd.read_sql(
    "SELECT user_id, ingredient_id "
    "FROM user_ingredient "
    "WHERE quantity=1", conn
)
user_history_df = pd.read_sql(""
    "SELECT user_id, recipe_id, cook_time "
    "FROM user_history", conn)
user_fav_df = pd.read_sql(""
    "SELECT user_id, recipe_id "
    "FROM userfavoriterecipe", conn)
ingredient_idf_df = pd.read_sql(""
    "SELECT ingredient_id, idf_value "
    "FROM ingredient_idf", conn)
# 构建映射。
recipe_tags_map = {}

for _, r in recipe_tags_df.iterrows():
    recipe_tags_map.setdefault(r.recipe_id, {})[r.tag_id] = r.match_amount

user_tags_map = {}

for _, r in user_tags_df.iterrows():
    user_tags_map.setdefault(r.user_id, set()).add(r.tag_id)

recipe_ingredients_map = {}

for _, r in recipe_ingredients_df.iterrows():
    recipe_ingredients_map.setdefault(r.recipe_id, set()).add(r.ingredient_id)

user_ingredients_map = {}

for _, r in user_ingredients_df.iterrows():
    user_ingredients_map.setdefault(r.user_id, set()).add(r.ingredient_id)

ingredient_idf_map = dict(zip(ingredient_idf_df.ingredient_id, ingredient_idf_df.idf_value))
history_set = set(zip(user_history_df.user_id, user_history_df.recipe_id))
fav_set = set(zip(user_fav_df.user_id, user_fav_df.recipe_id))
popularity_map = dict(zip(recipes_df.recipe_id, recipes_df.popularity))
user_session_map = (
    user_history_df.sort_values("cook_time")
    .groupby("user_id")["recipe_id"]
    .apply(list)
    .to_dict()
)
# 生成训练数据。
train_rows = []

for user_id in user_tags_map.keys():
    # 历史行为正样本
    history_pos = set(r for u, r in history_set if u == user_id)
    fav_pos = set(r for u, r in fav_set if u == user_id)
    behavior_pos = history_pos | fav_pos

    # 基于食材的高匹配正样本（阈值降低到0.35以适应稀疏数据）
    user_ings = user_ingredients_map.get(user_id, set())
    ingredient_pos = set()
    for recipe_id in recipe_ids:
        recipe_ings = recipe_ingredients_map.get(recipe_id, set())
        ing_score = calculate_ingredient_match(user_ings, recipe_ings, ingredient_idf_map)
        tag_score = calculate_tag_match(
            user_tags_map.get(user_id, set()),
            recipe_tags_map.get(recipe_id, {})
        )
        # 进一步降低阈值，让更多样本进入正样本池
        if ing_score > 0.25 or (ing_score > 0.15 and tag_score > 0.35):
            ingredient_pos.add(recipe_id)

    all_pos_recipes = behavior_pos | ingredient_pos
    neg_candidates = list(set(recipe_ids) - all_pos_recipes)

    # 分层负采样：70%基于ingredient_score加权，30%随机
    neg_sample_count = min(len(all_pos_recipes) * NEG_SAMPLE_RATIO, len(neg_candidates))
    if neg_sample_count > 0 and len(neg_candidates) > 0:
        random_neg_count = int(neg_sample_count * 0.3)
        weighted_neg_count = neg_sample_count - random_neg_count

        # 随机负样本
        random_neg = random.sample(neg_candidates, min(random_neg_count, len(neg_candidates)))

        # 加权负样本：ingredient_score>0的样本权重是0的20倍
        neg_weights = []
        for recipe_id in neg_candidates:
            recipe_ings = recipe_ingredients_map.get(recipe_id, set())
            ing_score = calculate_ingredient_match(user_ings, recipe_ings, ingredient_idf_map)
            # 进一步提高非零样本权重
            weight = 20.0 if ing_score > 0 else 1.0
            neg_weights.append(weight)

        total_weight = sum(neg_weights)
        if total_weight > 0:
            probs = [w / total_weight for w in neg_weights]
            remaining_count = min(weighted_neg_count, len(neg_candidates))
            weighted_neg = np.random.choice(
                neg_candidates,
                size=remaining_count,
                replace=False,
                p=probs
            )
            neg_sampled = list(set(random_neg) | set(weighted_neg))
        else:
            neg_sampled = random_neg
    else:
        neg_sampled = []

    candidate_recipes = all_pos_recipes | set(neg_sampled)

    for recipe_id in candidate_recipes:
        tag_score = calculate_tag_match(
            user_tags_map.get(user_id, set()),
            recipe_tags_map.get(recipe_id, {})
        )
        ingredient_score = calculate_ingredient_match(
            user_ingredients_map.get(user_id, set()),
            recipe_ingredients_map.get(recipe_id, set()),
            ingredient_idf_map
        )
        hot_score = math.log1p(popularity_map.get(recipe_id, 0)) / 10.0
        # 特征增强：大幅放大tag和hot，同时压缩ingredient
        tag_score_enhanced = (tag_score ** 0.5) * 1.5  # 幂变换后再放大
        hot_score_enhanced = hot_score * 5.0  # 进一步放大到5倍
        # 对ingredient进行压缩：使用平方根变换降低极端值影响
        if ingredient_score < 0:
            ingredient_score_enhanced = 0.0
        else:
            ingredient_score_enhanced = ingredient_score ** 1.2  # 轻微放大高值，但整体压缩分布

        # 离散label定义：细粒度划分ingredient_score区间
        # 基础label：主要基于ingredient_score分段
        if ingredient_score <= 0:
            base_label = 0
        elif ingredient_score <= 0.15:
            base_label = 1
        elif ingredient_score <= 0.3:
            base_label = 2
        elif ingredient_score <= 0.45:
            base_label = 3
        elif ingredient_score <= 0.6:
            base_label = 4
        elif ingredient_score <= 0.75:
            base_label = 5
        else:  # > 0.75
            base_label = 6

        # tag_score加成（最多+1）
        tag_bonus = 0
        if base_label > 0:
            if tag_score <= 0.25:
                tag_bonus = -1
            elif tag_score >= 0.5:
                tag_bonus = 1
            elif tag_score >= 0.75:
                tag_bonus = 2

        # 历史行为加成
        behavior_bonus = 0
        if recipe_id in history_pos:
            behavior_bonus = 3  # 烹饪过加3级
        elif recipe_id in fav_pos:
            behavior_bonus = 2  # 收藏过加2级

        # 最终label（最大值限制在10以内）
        label = min(base_label + tag_bonus + behavior_bonus, 10)
        session_score = session_encoder.get_session_scores(
            session_seq = user_session_map.get(user_id, []),
            candidate_ids=[recipe_id]
        )[0]
        train_rows.append({
            "user_id": user_id,
            "recipe_id": recipe_id,
            "tag_score": tag_score_enhanced,
            "ingredient_score": ingredient_score_enhanced,
            "hot_score": hot_score_enhanced,
            "session_score": session_score,
            "label": label
        })
# 对高ingredient_score样本过采样
train_df_original = pd.DataFrame(train_rows)

# 计算原始特征值用于判断（需要从增强特征反推或重新计算）
high_ing_samples = []
for row in train_rows:
    # ingredient_score_enhanced = ingredient_score ** 1.2
    # 反推原始值：ingredient_score = ingredient_score_enhanced ** (1/1.2)
    original_ing_score = row['ingredient_score'] ** (1/1.2)
    if original_ing_score > 0.3:
        high_ing_samples.append(row)

print(f"高ingredient_score样本数（>0.3）：{len(high_ing_samples)}")

# 过采样：复制这些样本8次（提高到8次）
oversampled_rows = train_rows.copy()
for _ in range(8):
    oversampled_rows.extend(high_ing_samples)

print(f"过采样前样本数：{len(train_rows)}")
print(f"过采样后样本数：{len(oversampled_rows)}")

train_df = pd.DataFrame(oversampled_rows)
train_df.to_csv("train_rank_data.csv", index=False)
print(f"训练数据生成完成：train_rank_data.csv，共{len(train_df)}条")
print(f"\n各label分布：")
print(train_df['label'].value_counts().sort_index())
print(f"\ningredient_score非零样本数：{(train_df['ingredient_score'] > 0).sum()} / {len(train_df)} ({(train_df['ingredient_score'] > 0).sum()/len(train_df)*100:.1f}%)")
print(f"ingredient_score > 0.2 样本数：{(train_df['ingredient_score'] > 0.2).sum()}")
print(f"ingredient_score > 0.5 样本数：{(train_df['ingredient_score'] > 0.5).sum()}")
print(f"\n特征值范围：")
print(f"tag_score: [{train_df['tag_score'].min():.3f}, {train_df['tag_score'].max():.3f}]")
print(f"ingredient_score: [{train_df['ingredient_score'].min():.3f}, {train_df['ingredient_score'].max():.3f}]")
print(f"hot_score: [{train_df['hot_score'].min():.3f}, {train_df['hot_score'].max():.3f}]")