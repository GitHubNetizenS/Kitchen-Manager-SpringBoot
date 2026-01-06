package com.kitchen_manager.entity;

import lombok.Data;
import java.sql.Timestamp;
import jakarta.persistence.*;

/**
 * 用户食材库存表
 */
@Data
@Entity
@Table(name = "user_ingredient")
public class UserIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer stockId;        // 库存记录ID

    @Column(name = "user_id")
    private Integer userId;         // 关联用户表

    @Column(name = "ingredient_id")
    private Integer ingredientId;   // 关联食材表

    @Column(name = "quantity")
    private Integer quantity;       // 当前食材是否仍有存量

    @Column(name = "storage_time")
    private Timestamp storageTime;  // 入库时间

    @Column(name = "custom_expiry_days")
    private Integer customExpiryDays; //新增保质期字段
}