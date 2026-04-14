import json     as json
import torch    as torch
import pandas   as pd
import torch.nn as nn
from gru4rec_model      import GRU4Rec
from torch.utils.data   import Dataset, DataLoader

N = 5
EPOCHS = 50
BATCH_SIZE = 32
EMBED_DIM = 32
HIDDEN_DIM = 64

class SessionDataset(Dataset):
    def __init__(self, history_csv, n=N):
        df = pd.read_csv(history_csv)
        df = df.sort_values(["user_id", "time"])
        self.samples = []

        for _, group in df.groupby("user_id"):
            ids = group["recipe_id"].tolist()

            for i in range(1, len(ids)):
                seq = ids[max(0, i-n): i: 1]
                seq = [0]*(n-len(seq)) + seq
                target = ids[i]

                self.samples.append((seq, target))

    def __len__(self):

        return len(self.samples)

    def __getitem__(self, idx):
        seq, target = self.samples[idx]

        return torch.tensor(seq), torch.tensor(target)


def train(history_csv, model_output_path, num_items):
    dataset = SessionDataset(history_csv)
    loader = DataLoader(dataset, batch_size=BATCH_SIZE, shuffle=True)
    model = GRU4Rec(num_items=num_items, embed_dim=EMBED_DIM, hidden_dim=HIDDEN_DIM)
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    loss_fn = nn.CrossEntropyLoss()

    for epoch in range(EPOCHS):
        total_loss = 0

        for seq, target in loader:
            logits = model(seq)
            loss = loss_fn(logits, target)

            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

            total_loss += loss.item()
        print(f"Epoch {epoch+1}/{EPOCHS}, Loss: {total_loss/len(loader):.4f}")

    torch.save(model.state_dict(), model_output_path)
    print(f"GRU4Rec模型已保存至：{model_output_path}。")
    with open("gru4rec_config.json", 'w') as f:
        json.dump({"num_items": num_items}, f)
    print(f"配置已保存至 gru4rec_config.json。")

if __name__ == "__main__":
    df = pd.read_csv("session_history.csv")
    num_items = int(df['recipe_id'].max())
    print(f"自动检测到最大recipe_id: {num_items}，将作为num_items参数。")
    train(
        history_csv="session_history.csv",
        model_output_path="gru4rec_model.pth",
        num_items=num_items
    )