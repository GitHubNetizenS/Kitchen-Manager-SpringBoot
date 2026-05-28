import torch.nn as nn

class GRU4Rec(nn.Module):
    def __init__(self, num_items, embed_dim=32, hidden_dim=64, dropout=0.2):
        super().__init__()
        self.embed_dim = embed_dim  # 新增：保存embed_dim供其他方法使用
        self.item_embed = nn.Embedding(num_items + 1, embed_dim, padding_idx=0)
        self.gru = nn.GRU(embed_dim, hidden_dim, batch_first=True)
        self.dropout = nn.Dropout(dropout)
        self.output_layer = nn.Linear(hidden_dim, num_items + 1)
        self.proj = nn.Linear(hidden_dim, embed_dim)  # 新增：将隐状态投影到embed维度

    def forward(self, session_seq):
        x = self.dropout(self.item_embed(session_seq))
        _, h_n = self.gru(x)
        last_hidden = self.dropout(h_n.squeeze(0))
        logits = self.output_layer(last_hidden)

        return logits

    def get_session_embedding(self, session_seq):
        x = self.item_embed(session_seq)
        _, h_n = self.gru(x)
        h = h_n.squeeze(0)   # [B, hidden_dim]
        return self.proj(h)  # [B, embed_dim]，与item_embed维度对齐