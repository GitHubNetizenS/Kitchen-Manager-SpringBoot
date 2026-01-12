package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "recipe_video")
public class RecipeVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "recipe_id", nullable = false)
    private Integer recipeId;

    @Column(name = "platform", length = 20, nullable = false)
    private String platform;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "video_url", columnDefinition = "TEXT", nullable = false)
    private String videoUrl;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Column(name = "play_count")
    private Integer playCount = 0;

    @Column(name = "duration", length = 20)
    private String duration;

    @Column(name = "score")
    private Integer score = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}