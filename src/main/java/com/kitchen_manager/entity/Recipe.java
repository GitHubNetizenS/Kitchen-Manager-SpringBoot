package com.kitchen_manager.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import jakarta.persistence.*;
import java.util.List;

import java.util.ArrayList;

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
    @JsonProperty("recipe_id")
    private Integer recipeId;   // 菜谱唯一标识

    @Column(name = "name")
    private String name;        // 菜谱名称

    @Column(name = "image_url")
    @JsonProperty("image_url")
    private String imageUrl;    // 封面图片URL

    @Column(name = "taste")
    private String taste;       // 口味（如咸香或酸甜等。）

    @Column(name = "method")
    private String method;      // 工艺（如炒、煮或蒸等。）

    @Column(name = "time")
    private String time;        // 烹饪时间（单位：分钟）

    @Column(name = "difficulty")
    private String difficulty;  // 烹饪难度（如简单、中等或困难等。）

    @Column(name = "needs", columnDefinition = "json")
    private String needs;       // 包含的原料

    @Column(name = "steps", columnDefinition = "json")
    private String steps;       // 步骤说明（JSON数组）

    @Column(name = "popularity")
    private Integer popularity; // 热度

    @Transient
    private double tagMatchScore;           // 标签匹配度

    @Transient
    private double ingredientMatchScore;    // 原料匹配度

    @Transient
    private double hotScore;                // 热度特征

    @Transient
    private double predictScore;            // 预测综合评分

    @Transient
    private List<RecipeTag> recipeTags;

    @Transient
    private List<Integer> ingredientIds;

    /**
     * 解析 needs JSON 字段为 List
     */
    public List<String> getNeedsAsList() {
        if (needs == null || needs.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(needs, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            System.err.println("解析 needs 字段失败: " + needs);
            return new ArrayList<>();
        }
    }

    /**
     * 解析 steps JSON 字段为 List
     */
    public List<String> getStepsAsList() {
        if (steps == null || steps.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(steps, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            System.err.println("解析 steps 字段失败: " + steps);
            return new ArrayList<>();
        }
    }
}