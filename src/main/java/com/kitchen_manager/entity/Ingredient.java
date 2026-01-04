package com.kitchen_manager.entity;

import lombok.Data;
import jakarta.persistence.*;

/**
 * 食材表
 */
@Data
@Entity
@Table(name = "ingredient")
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private Integer ingredientId;   // 食材唯一标识（主键）

    @Column(name = "name")
    private String name;            // 食材名称

    @Column(name = "nutrition")
    private String nutrition;       // 营养成分（JSON格式）

    @Column(name = "health_benefit")
    private String healthBenefit;   // 食疗功效

    @Column(name = "image_url")
    private String imageUrl;        // 图片URL地址

    @Column(name = "main_category")
    private String mainCategory;    // 分类（如果蔬、肉蛋或主食等。）

    @Column(name = "expiry_days")
    private Integer expiryDays;     // 保质期（单位：天数）

    @Column(name = "sub_category")
    private String subCategory;     // 子分类（以肉蛋为例，还可分为猪肉、蛋类或乳制品等。）
}