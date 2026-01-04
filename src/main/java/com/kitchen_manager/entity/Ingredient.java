package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "ingredient")
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private Integer ingredientId;

    @Column(name = "name")
    private String name;

    @Column(name = "nutrition")
    private String nutrition;

    @Column(name = "health_benefit")
    private String healthBenefit;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "main_category")
    private String mainCategory;

    @Column(name = "expiry_days")
    private Integer expiryDays;

    @Column(name = "sub_category")
    private String subCategory;
}