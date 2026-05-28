import torch as torch
from gru4rec_model import GRU4Rec

N = 5

class SessionEncoder:
    def __init__(self, model_path, num_items, hidden_dim=64):
        self.model = GRU4Rec(num_items=num_items, hidden_dim=hidden_dim)

        self.model.load_state_dict(torch.load(model_path, map_location="cpu"))
        self.model.eval()
        with torch.no_grad():
            all_ids = torch.arange(1, num_items+1, 1)

            self.item_embeddings = self.model.item_embed(all_ids)

    def get_session_scores(self, session_seq: list, candidate_ids: list) -> list:
        seq = [0] * (N - len(session_seq)) + session_seq[-N:]
        seq_tensor = torch.tensor([seq])

        with torch.no_grad():
            h = self.model.get_session_embedding(seq_tensor)  # [1, H]

            scores = []
            for rid in candidate_ids:
                if rid < 1 or rid > len(self.item_embeddings):
                    scores.append(0.0)
                    continue
                # 取该候选菜谱的embedding
                item_emb = self.item_embeddings[rid - 1].unsqueeze(0)  # [1, E]
                # 计算余弦相似度
                cos_sim = torch.nn.functional.cosine_similarity(
                    h, item_emb, dim=-1
                ).item()
                # 映射到[0,1]区间
                scores.append((cos_sim + 1.0) / 2.0)

        return scores