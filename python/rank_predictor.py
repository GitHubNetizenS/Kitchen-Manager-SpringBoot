import sys
import json
import traceback
import numpy as np
import lightgbm as lgb

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
        features_json = sys.argv[2]

        print(f"DEBUG: 模型路径: {model_path}", file=sys.stderr)
        print(f"DEBUG: JSON长度: {len(features_json)}", file=sys.stderr)

        # 加载模型
        print("DEBUG: 正在加载模型...", file=sys.stderr)
        model = lgb.Booster(model_file=model_path)
        print("DEBUG: 模型加载成功", file=sys.stderr)

        # 解析JSON
        feature_list = json.loads(features_json)
        print(f"DEBUG: 解析到 {len(feature_list)} 个样本", file=sys.stderr)

        if not feature_list:
            print("[]")
            return

        # 转换为numpy数组
        X = np.array(feature_list, dtype=np.float32)
        print(f"DEBUG: 输入形状: {X.shape}", file=sys.stderr)

        # 预测
        scores = model.predict(X)
        print(f"DEBUG: 预测完成，得到 {len(scores)} 个分数", file=sys.stderr)

        # 输出结果（只输出JSON，不包含调试信息）
        result = json.dumps(scores.tolist())
        print(result)

    except Exception as e:
        print(f"ERROR: 脚本执行失败: {str(e)}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()