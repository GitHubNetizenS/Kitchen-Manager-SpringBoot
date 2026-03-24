import pandas as pd
import lightgbm as lgb

# 训练LightGBM Rank模型并保存。
# train_csv必须包含字段：user_id、recipe_id、tag_score、ingredient_score、hot_score和label。
def train_lgbm_rank(train_csv_path: str, model_output_path: str):
    # 1. 读取训练数据。
    df = pd.read_csv(train_csv_path)
    feature_cols = [
        "tag_score",                # 标签匹配度
        "ingredient_score",         # 原料匹配度
        "hot_score",                # 热度特征
        "session_score"             # 最近会话时间特征
    ]
    X = df[feature_cols].values
    y = df["label"].values
    # 2. 构造group（按user_id分组）。
    # LightGBM Rank的核心：每个user是一个query。
    group_sizes = (
        df.groupby("user_id")
          .size()
          .values
    )
    train_dataset = lgb.Dataset(
        X,
        label=y,
        group=group_sizes,
        feature_name=feature_cols
    )
    # 3. Rank模型参数。
    params = {
        "objective": "lambdarank",  # 使用LambdaRank排序算法，专门解决排序问题。
        "metric": "ndcg",           # 评估指标：NDCG，越高越好，满分1.0。
        "ndcg_eval_at": [3, 5, 10],
        "learning_rate": 0.03,      # 学习率（小步前进，学得稳）。
        "num_leaves": 63,           # 树的最大叶子数（控制模型复杂度）。
        "max_depth": 8,
        "min_data_in_leaf": 10,     # 每个叶子最少样本数（防止过拟合）。
        "verbosity": -1,            # 不输出训练过程（安静模式）。
        "seed": 666,                # 随机种子（保证结果可重复）。
        "monotone_constraints": [1, 1, 1, 1],
        "feature_fraction": 0.9,
        "lambda_l1": 0.05,
        "lambda_l2": 0.05
    }
    # 4. 训练模型。
    model = lgb.train(
        params,
        train_dataset,
        num_boost_round=250
    )
    # 5. 保存模型。
    model.save_model(model_output_path)
    # 6. 输出特征重要性（用于课程展示）
    importance = model.feature_importance(importance_type="gain")
    importance_df = pd.DataFrame({
        "feature": feature_cols,
        "importance": importance
    }).sort_values(by="importance", ascending=False)

    print("训练完成，模型已保存至：", model_output_path, '。')
    print("特征重要性：")
    print(importance_df)

if __name__ == "__main__":
    train_lgbm_rank(train_csv_path="./train_rank_data.csv", model_output_path="./lightgbm_rank_model.txt")