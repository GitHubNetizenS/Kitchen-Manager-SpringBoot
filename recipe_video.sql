CREATE TABLE recipe_video (
                              id INT AUTO_INCREMENT PRIMARY KEY,
                              recipe_id INT NOT NULL,
                              platform VARCHAR(20) NOT NULL,
                              title VARCHAR(255) NOT NULL,
                              video_url TEXT NOT NULL,
                              cover_url TEXT,
                              play_count INT DEFAULT 0,
                              duration VARCHAR(20),
                              score INT DEFAULT 0,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                              UNIQUE KEY uk_recipe_platform (recipe_id, platform),
                              FOREIGN KEY (recipe_id) REFERENCES recipe(recipe_id)
);
ALTER TABLE recipe_video
    MODIFY title VARCHAR(255)
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;
