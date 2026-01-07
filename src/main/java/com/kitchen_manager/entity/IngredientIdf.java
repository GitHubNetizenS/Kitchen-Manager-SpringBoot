package com.kitchen_manager.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * ingredient_idf实体类
 */
@Data
@Entity
@Table(name = "ingredient_idf")
public class IngredientIdf {
    @Id
    @Column(name = "ingredient_id")
    private Integer ingredientId;

    @Column(name = "idf_value")
    private Double idfValue;

    public IngredientIdf(Integer ingredientId, Double idfValue) {
        this.ingredientId = ingredientId;
        this.idfValue = idfValue;
    }

    protected IngredientIdf() {

    }
}