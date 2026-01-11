package com.kitchen_manager.elasticsearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "recipes")  // 索引名称
public class RecipeDocument {

    @Id
    private Integer recipeId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")  //ik_max_word最细粒度拆分以索引，ik_smart粗粒度拆分以搜索
    private String name;  // 菜谱名

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String ingredients;  // 食材列表（逗号分隔）

    @Field(type = FieldType.Keyword)
    private String taste;  // 口味

    @Field(type = FieldType.Keyword)
    private String method;  // 烹饪方法

    @Field(type = FieldType.Keyword)
    private String difficulty;  // 难度

    @Field(type = FieldType.Integer)
    private Integer popularity;  // 热度

    @Field(type = FieldType.Text)
    private String imageUrl;  // 图片
}