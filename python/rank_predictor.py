import sys
import json
import traceback
import numpy as np
import lightgbm as lgb

def adjust_scores(raw_scores, features):
    adjusted_scores = raw_scores.copy()

    for i in range(len(raw_scores)):
        tag_score = features[i][0]
        #ingredient_score = features[i][1]
        hot_score = features[i][2]

        # 基础调整：tag和hot的加权
        # tag_score高于1.0时额外加分（因为经过了1.5倍增强，原始>0.67就会>1.0）
        tag_bonus = 0.0
        if tag_score > 1.2:
            tag_bonus = 0.15
        elif tag_score > 1.0:
            tag_bonus = 0.10
        elif tag_score > 0.8:
            tag_bonus = 0.05

        # hot_score高于1.5时额外加分（经过了5倍增强）
        hot_bonus = 0.0
        if hot_score > 1.5:
            hot_bonus = 0.10
        elif hot_score > 1.2:
            hot_bonus = 0.05

        # 组合调整：如果tag和hot都很高，给额外的协同加成
        if tag_score > 1.0 and hot_score > 1.2:
            combo_bonus = 0.08
        else:
            combo_bonus = 0.0

        # 总调整不超过原始分数的30%，避免过度干预
        total_adjustment = tag_bonus + hot_bonus + combo_bonus
        max_adjustment = abs(raw_scores[i]) * 0.3
        total_adjustment = min(total_adjustment, max_adjustment)

        adjusted_scores[i] = raw_scores[i] + total_adjustment

    return adjusted_scores

def main():
    try:
        # 打印所有参数用于调试
        print(f"DEBUG: 接收到的参数数量: {len(sys.argv)}", file=sys.stderr)
        print(f"DEBUG: 参数列表: {sys.argv}", file=sys.stderr)

        # Java传递的参数是：
        # sys.argv[0] = 脚本文件名 (rank_predictor.py)
        # sys.argv[1] = 模型路径
        # sys.argv[2] = 特征JSON字符串

        if len(sys.argv) < 3:
            print(f"错误: 需要至少2个参数，但只收到 {len(sys.argv)-1} 个", file=sys.stderr)
            sys.exit(1)

        model_path = sys.argv[1]
        features_file_path = sys.argv[2]

        print(f"DEBUG: 模型路径: {model_path}", file=sys.stderr)

        # 加载模型
        print("DEBUG: 正在加载模型...", file=sys.stderr)
        model = lgb.Booster(model_file=model_path)
        print("DEBUG: 模型加载成功", file=sys.stderr)

        # 解析JSON
        with open(features_file_path, "r") as f:
            feature_list = json.load(f)
        print(f"DEBUG: 解析到 {len(feature_list)} 个样本", file=sys.stderr)

        if not feature_list:
            print("[]")
            return

        # 转换为numpy数组
        X = np.array(feature_list, dtype=np.float32)
        print(f"DEBUG: 输入形状: {X.shape}", file=sys.stderr)

        # 预测
        raw_scores = model.predict(X)
        print(f"DEBUG: 预测完成，得到 {len(raw_scores)} 个分数", file=sys.stderr)

        # 二次排序调整
        adjusted_scores = adjust_scores(raw_scores, X)
        print(f"DEBUG: 二次排序完成", file=sys.stderr)

        # 输出调整后的结果
        result = json.dumps(adjusted_scores.tolist())
        print(result)

    except Exception as e:
        print(f"ERROR: 脚本执行失败: {str(e)}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()