import requests
import time
import random
import pymysql
from urllib.parse import quote
from concurrent.futures import ThreadPoolExecutor, as_completed
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')


class RecipeVideoCrawler:
    def __init__(self, db_config=None):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept": "application/json",
            "Referer": "https://www.bilibili.com"
        }

        # 数据库配置
        if db_config is None:
            db_config = {
                "host": "localhost",
                "user": "root",
                "password": "123456",
                "database": "kitchen-manager",
                "charset": "utf8mb4"
            }

        self.db_config = db_config
        self.db = pymysql.connect(**db_config)

    # ---------------- 搜索优化 ----------------

    def search_bilibili(self, keyword, limit=10):
        encoded = quote(keyword)
        url = (
            "https://api.bilibili.com/x/web-interface/search/type"
            f"?search_type=video&keyword={encoded}"
        )

        max_retries = 3
        for attempt in range(max_retries):
            try:
                r = requests.get(url, headers=self.headers, timeout=10)
                data = r.json()

                if data.get("code") != 0:
                    if attempt < max_retries - 1:
                        time.sleep(1)
                        continue
                    return []

                results = data.get("data", {}).get("result", [])
                videos = []

                for item in results[:limit]:
                    videos.append({
                        "title": item.get("title", "")
                        .replace('<em class="keyword">', '')
                        .replace('</em>', ''),
                        "bvid": item.get("bvid"),
                        "url": f"https://www.bilibili.com/video/{item.get('bvid')}",
                        "play": item.get("play", 0),
                        "duration": self._parse_duration(item.get("duration", "")),
                        "author": item.get("author", ""),
                        "cover": item.get("pic", ""),
                        "pubdate": item.get("pubdate", 0)
                    })

                return videos

            except Exception as e:
                logging.error(f"搜索失败 (尝试 {attempt + 1}/{max_retries}): {keyword} - {e}")
                if attempt < max_retries - 1:
                    time.sleep(2)
                else:
                    return []

        return []

    def _parse_duration(self, duration_str):
        """统一时长格式为秒数"""
        if isinstance(duration_str, int):
            return duration_str

        try:
            if ":" in str(duration_str):
                parts = str(duration_str).split(":")
                if len(parts) == 2:
                    return int(parts[0]) * 60 + int(parts[1])
                elif len(parts) == 3:
                    return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
        except:
            pass

        return 0

    # ---------------- 评分优化 ----------------

    def score_video(self, video, recipe_name):
        """
        简化评分算法:专注于菜名匹配度和教程特征
        """
        score = 0
        title = video["title"].lower()
        recipe_lower = recipe_name.lower()

        # 1. 菜名匹配度 (最核心,70分)
        if recipe_lower in title:
            # 完整菜名命中,基础50分
            score += 50

            # 如果标题主要就是菜名(短标题),额外加分
            if len(title) < len(recipe_lower) + 10:
                score += 10

            # 如果菜名在标题开头,额外加分
            if title.startswith(recipe_lower):
                score += 10
        else:
            # 没有完整匹配,检查菜名的每个字
            # 例如"红烧肉"没匹配到,但"红烧"或"烧肉"匹配了
            chars_matched = sum(1 for char in recipe_lower if char in title)
            match_ratio = chars_matched / len(recipe_lower)
            score += int(match_ratio * 30)  # 最多30分

        # 2. 教程特征关键词 (30分)
        tutorial_keywords = {
            "教程": 15,
            "做法": 15,
            "家常": 10,
            "详细": 8,
            "步骤": 6,
            "新手": 5,
            "零失败": 5,
            "简单": 4,
            "厨房": 3
        }
        for kw, points in tutorial_keywords.items():
            if kw in title:
                score += points
                break  # 只加一次最高分,避免重复

        # 3. 负面关键词扣分 (排除非教程视频)
        negative_keywords = ["吃播", "探店", "测评", "开箱", "vlog", "挑战", "大胃王", "吃货"]
        for kw in negative_keywords:
            if kw in title:
                score -= 30  # 严格扣分

        return max(0, score)  # 确保分数不为负

    # ---------------- 智能搜索策略 ----------------

    def smart_search(self, recipe_name):
        """
        简化搜索策略:
        1. 菜名 + 教程/做法
        2. 直接搜菜名
        3. 去重
        """
        all_videos = {}  # 用bvid去重

        # 策略1: 菜名 + 教程 (最精准)
        videos1 = self.search_bilibili(f"{recipe_name} 教程", limit=10)
        for v in videos1:
            if v["bvid"] not in all_videos:
                all_videos[v["bvid"]] = v

        time.sleep(0.3)

        # 策略2: 菜名 + 做法 (备选)
        if len(all_videos) < 8:
            videos2 = self.search_bilibili(f"{recipe_name} 做法", limit=10)
            for v in videos2:
                if v["bvid"] not in all_videos:
                    all_videos[v["bvid"]] = v

            time.sleep(0.3)

        # 策略3: 直接搜菜名 (兜底)
        if len(all_videos) < 5:
            videos3 = self.search_bilibili(recipe_name, limit=10)
            for v in videos3:
                if v["bvid"] not in all_videos:
                    all_videos[v["bvid"]] = v

        return list(all_videos.values())

    # ---------------- 核心逻辑优化 ----------------

    def crawl_one(self, recipe_id, recipe_name):
        """
        优化单个菜谱爬取:
        1. 使用简化搜索策略
        2. 综合评分选择最佳视频
        3. 返回详细信息供调试
        """
        logging.info(f"爬取: {recipe_name}")

        # 智能搜索
        videos = self.smart_search(recipe_name)

        if not videos:
            logging.warning(f"⚠️ 未找到视频: {recipe_name}")
            return None

        # 打分排序
        for v in videos:
            v["score"] = self.score_video(v, recipe_name)

        videos.sort(key=lambda x: x["score"], reverse=True)

        best = videos[0]
        best["recipe_id"] = recipe_id

        # 显示前3个候选供参考
        logging.info(f"✓ 选中: {best['title'][:40]}... (分数:{best['score']}, 播放:{best['play']})")
        if len(videos) > 1:
            logging.info(f"  备选2: {videos[1]['title'][:40]}... (分数:{videos[1]['score']})")
        if len(videos) > 2:
            logging.info(f"  备选3: {videos[2]['title'][:40]}... (分数:{videos[2]['score']})")

        return best

    # ---------------- 数据库操作优化 ----------------

    def save_video(self, video):
        """保存单个视频到数据库"""
        if video is None:
            return

        sql = """
              INSERT INTO recipe_video
              (recipe_id, platform, title, video_url, cover_url, play_count, duration, score)
              VALUES (%s, 'bilibili', %s, %s, %s, %s, %s, %s)
                  ON DUPLICATE KEY UPDATE
                                       title=VALUES(title),
                                       video_url=VALUES(video_url),
                                       play_count=VALUES(play_count),
                                       score=VALUES(score),
                                       cover_url=VALUES(cover_url) \
              """

        with self.db.cursor() as cursor:
            cursor.execute(sql, (
                video["recipe_id"],
                video["title"],
                video["url"],
                video["cover"],
                video["play"],
                video["duration"],
                video["score"]
            ))

        self.db.commit()

    # ---------------- 增量爬取 ----------------

    def get_recipes_without_video(self):
        """
        获取还没有视频的菜谱
        """
        sql = """
              SELECT r.recipe_id, r.name
              FROM recipe r
                       LEFT JOIN recipe_video rv ON r.recipe_id = rv.recipe_id
              WHERE rv.id IS NULL \
              """

        with self.db.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(sql)
            return cursor.fetchall()

    def get_low_quality_videos(self, min_score=30):
        """
        获取评分过低的视频,重新爬取
        """
        sql = """
              SELECT r.recipe_id, r.name, rv.score
              FROM recipe r
                       JOIN recipe_video rv ON r.recipe_id = rv.recipe_id
              WHERE rv.score < %s \
              """

        with self.db.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(sql, (min_score,))
            return cursor.fetchall()

    # ---------------- 批量处理优化 ----------------

    def batch_crawl(self, recipes, delay_range=(0.3, 0.6)):
        """
        串行批量爬取
        """
        success = 0
        failed = 0

        for i, r in enumerate(recipes):
            logging.info(f"\n进度 {i + 1}/{len(recipes)}")

            try:
                video = self.crawl_one(r["recipe_id"], r["name"])
                if video:
                    self.save_video(video)
                    success += 1
                else:
                    failed += 1
            except Exception as e:
                logging.error(f"失败: {r['name']} - {e}")
                failed += 1

            # 随机延迟避免封IP
            if i < len(recipes) - 1:
                time.sleep(random.uniform(*delay_range))

        logging.info(f"\n完成! 成功:{success}, 失败:{failed}")

    def batch_crawl_parallel(self, recipes, max_workers=3):
        """
        并发爬取 (谨慎使用,可能被封IP)
        max_workers建议不超过3
        """
        success = 0
        failed = 0

        def process_one(r):
            try:
                video = self.crawl_one(r["recipe_id"], r["name"])
                if video:
                    # 每个线程单独连接数据库
                    db = pymysql.connect(**self.db_config)
                    cursor = db.cursor()

                    sql = """
                          INSERT INTO recipe_video
                          (recipe_id, platform, title, video_url, cover_url, play_count, duration, score)
                          VALUES (%s, 'bilibili', %s, %s, %s, %s, %s, %s)
                              ON DUPLICATE KEY UPDATE
                                                   title=VALUES(title),
                                                   video_url=VALUES(video_url),
                                                   play_count=VALUES(play_count),
                                                   score=VALUES(score) \
                          """

                    cursor.execute(sql, (
                        video["recipe_id"], video["title"], video["url"],
                        video["cover"], video["play"], video["duration"], video["score"]
                    ))

                    db.commit()
                    cursor.close()
                    db.close()

                    return True
            except Exception as e:
                logging.error(f"失败: {r['name']} - {e}")
                return False

        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {executor.submit(process_one, r): r for r in recipes}

            for i, future in enumerate(as_completed(futures)):
                if future.result():
                    success += 1
                else:
                    failed += 1

                logging.info(f"进度: {i + 1}/{len(recipes)}")
                time.sleep(0.1)  # 短暂延迟

        logging.info(f"\n完成! 成功:{success}, 失败:{failed}")

    # ---------------- 便捷方法 ----------------

    def crawl_new_recipes(self):
        """爬取所有没有视频的菜谱"""
        recipes = self.get_recipes_without_video()
        logging.info(f"发现 {len(recipes)} 个新菜谱需要爬取")

        if recipes:
            self.batch_crawl(recipes)

    def refresh_low_quality(self, min_score=30):
        """重新爬取低质量视频"""
        recipes = self.get_low_quality_videos(min_score)
        logging.info(f"发现 {len(recipes)} 个低质量视频需要重新爬取")

        if recipes:
            self.batch_crawl(recipes)

    def close(self):
        """关闭数据库连接"""
        self.db.close()


# ---------------- 使用示例 ----------------

if __name__ == "__main__":
    # 数据库配置
    db_config = {
        "host": "localhost",
        "user": "root",
        "password": "123456",
        "database": "kitchen-manager",
        "charset": "utf8mb4"
    }

    crawler = RecipeVideoCrawler(db_config)

    # ========== 方式1: 增量爬取 (推荐) ==========
    # 只爬取还没有视频的菜谱
    logging.info("开始增量爬取...")
    crawler.crawl_new_recipes()

    # ========== 方式2: 测试单个菜谱 ==========
    # video = crawler.crawl_one(1, "红烧肉")
    # if video:
    #     crawler.save_video(video)

    # ========== 方式3: 手动指定菜谱 ==========
    # recipes = [
    #     {"recipe_id": 1, "name": "红烧肉"},
    #     {"recipe_id": 2, "name": "宫保鸡丁"}
    # ]
    # crawler.batch_crawl(recipes)

    # ========== 方式4: 并发爬取 (慎用) ==========
    #recipes = crawler.get_recipes_without_video()
    #crawler.batch_crawl_parallel(recipes, max_workers=3)

    # ========== 方式5: 刷新低质量视频 ==========
    # crawler.refresh_low_quality(min_score=30)

    crawler.close()