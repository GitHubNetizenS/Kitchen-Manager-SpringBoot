package com.kitchen_manager.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeElasticsearchRepository
        extends ElasticsearchRepository<RecipeDocument, Integer> {

    // Spring Data 自动实现方法
}