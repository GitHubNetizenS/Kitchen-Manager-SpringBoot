package com.kitchen_manager.entity;

import lombok.Data;
import jakarta.persistence.*;

/**
 * 菜谱表
 */
@Data
@Entity
@Table(name = "recipe")
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private Integer recipeId;   // 菜谱唯一标识

    @Column(name = "name")
    private String name;        // 菜谱名称

    @Column(name = "image_url")
    private String imageUrl;    // 封面图片URL

    @Column(name = "taste")
    private String taste;       // 口味（如咸香或酸甜等。）

    @Column(name = "method")
    private String method;      // 工艺（如炒、煮或蒸等。）

    @Column(name = "time")
    private String time;        // 烹饪时间（单位：分钟）

    @Column(name = "difficulty")
    private String difficulty;  // 烹饪难度（如简单、中等或困难等。）

    @Column(name = "needs")
    private String needs;       // 包含的原料

    @Column(name = "steps")
    private String steps;       // 步骤说明（JSON数组）

    @Column(name = "popularity")
    private Integer popularity; // 热度
}