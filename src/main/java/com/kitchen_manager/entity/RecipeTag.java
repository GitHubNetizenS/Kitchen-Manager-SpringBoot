package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "recipe_tag")
public class RecipeTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "recipe_id")
    private Integer recipeId;

    @Column(name = "tag_id")
    private Integer tagId;

    @Column(name = "match_amount")
    private Integer matchAmount;
}
