package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Entity
@Table(name = "recipe")
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private Integer recipeId;

    @Column(name = "name")
    private String name;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "taste")
    private String taste;

    @Column(name = "method")
    private String method;

    @Column(name = "time")
    private String time;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "needs")
    private String needs;

    @Column(name = "steps")
    private String steps;

    @Column(name = "popularity")
    private Integer popularity;
}
