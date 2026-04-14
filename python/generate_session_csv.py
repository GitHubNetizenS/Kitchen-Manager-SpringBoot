import pandas as pd
from sqlalchemy import create_engine

conn = create_engine(
    "mysql+pymysql://root:123456@localhost:3306/kitchen-manager?charset=utf8mb4"
)
history_df = pd.read_sql(
    "SELECT user_id, recipe_id, cook_time AS time FROM user_history",
    conn
)
history_df["behavior_type"] = "cook"
fav_df = pd.read_sql(
    "SELECT user_id, recipe_id, favorite_time AS time FROM userfavoriterecipe",
    conn
)
fav_df["behavior_type"] = "favorite"
merged_df = pd.concat([history_df, fav_df], ignore_index=True)
merged_df = merged_df.sort_values(["user_id", "time"]).reset_index(drop=True)
merged_df = merged_df.drop_duplicates(
    subset=["user_id", "recipe_id"],
    keep="first"
).reset_index(drop=True)
merged_df.to_csv("session_history.csv", index=False)

print(f"session_history.csv生成完成，共{len(merged_df)}条记录。")
print(merged_df.head(10))