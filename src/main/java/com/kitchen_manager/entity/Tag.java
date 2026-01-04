package com.kitchen_manager.entity;

import lombok.Data;
import jakarta.persistence.*;

/**
 * 标签表
 */
@Data
@Entity
@Table(name = "tag")
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;         // 标签主键ID

    @Column(name = "name")
    private String name;        // 标签名称（如早餐等。）

    @Column(name = "category")
    private String category;    // 标签类别（如用餐场景、偏好口味、用餐人群和特殊需求等。）
}