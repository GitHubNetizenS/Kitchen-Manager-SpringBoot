from flask import Flask, request, jsonify
from session_encoder import SessionEncoder
import lightgbm as lgb
import numpy as np
import time
import json

app = Flask(__name__)

# 全局变量:启动时加载模型,后续请求复用
model = None
model_path = None
session_encoder = None

def adjust_scores(raw_scores, features):
    """二次排序调整"""
    adjusted_scores = raw_scores.copy()

    for i in range(len(raw_scores)):
        tag_score = features[i][0]
        ingredient_score = features[i][1]
        hot_score = features[i][2]

        tag_bonus = 0.0
        if tag_score > 1.2:
            tag_bonus = 0.15
        elif tag_score > 1.0:
            tag_bonus = 0.10
        elif tag_score > 0.8:
            tag_bonus = 0.05

        hot_bonus = 0.0
        if hot_score > 1.5:
            hot_bonus = 0.10
        elif hot_score > 1.2:
            hot_bonus = 0.05

        if tag_score > 1.0 and hot_score > 1.2:
            combo_bonus = 0.08
        else:
            combo_bonus = 0.0

        total_adjustment = tag_bonus + hot_bonus + combo_bonus
        max_adjustment = abs(raw_scores[i]) * 0.3
        total_adjustment = min(total_adjustment, max_adjustment)

        adjusted_scores[i] = raw_scores[i] + total_adjustment

    return adjusted_scores

@app.route('/predict', methods=['POST'])
def predict():
    try:
        start_time = time.time()

        # 接收特征数据
        data = request.get_json()
        features = data.get('features', [])

        if not features:
            return jsonify({'error': '特征数据为空'}), 400

        # 转换为numpy数组
        session_seq = data.get("session_seq", [])
        candidate_ids = data.get("candidate_ids", [])
        session_scores = session_encoder.get_session_scores(session_seq, candidate_ids)
        for i, feat in enumerate(features):
            feat.append(session_scores[i])
        X = np.array(features, dtype=np.float32)

        # 预测
        raw_scores = model.predict(X)

        # 二次排序
        adjusted_scores = adjust_scores(raw_scores, X)

        elapsed = time.time() - start_time

        return jsonify({
            'scores': adjusted_scores.tolist(),
            'elapsed_ms': int(elapsed * 1000)
        })

    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/health', methods=['GET'])
def health():
    """健康检查接口"""
    return jsonify({'status': 'ok', 'model_loaded': model is not None})

def init_model(model_file_path, gru_model_path):
    global model, session_encoder
    model = lgb.Booster(model_file=model_file_path)
    with open("gru4rec_config.json", "r") as f:
        config = json.load(f)
    num_items = config["num_items"]
    session_encoder = SessionEncoder(gru_model_path, num_items=num_items)

if __name__ == '__main__':
    import sys

    if len(sys.argv) < 2:
        print("用法: python rank_service.py <LightGBM模型路径> [GRU4Rec模型路径] [端口号]")
        sys.exit(1)

    model_file = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 5000

    # 启动前加载模型
    gru_model_file = sys.argv[2] if len(sys.argv) > 2 else "gru4rec_model.pth"
    port = int(sys.argv[3]) if len(sys.argv) > 3 else 5000
    init_model(model_file, gru_model_file)

    # 启动服务
    print(f"启动HTTP服务,监听端口 {port}")
    app.run(host='0.0.0.0', port=port, debug=False)