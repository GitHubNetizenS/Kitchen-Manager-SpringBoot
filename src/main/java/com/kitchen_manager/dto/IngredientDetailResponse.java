package com.kitchen_manager.dto;

import lombok.Data;

@Data
public class IngredientDetailResponse {
    private String name;
    private String storageDate;
    private String expiryDate;
    private String nutrition;
    private String benefit;
    private String imageUrl;
    private String category;
}

