package com.kitchen_manager.config;

import com.kitchen_manager.elasticsearch.RecipeDocument;
import com.kitchen_manager.repository.RecipeRepository;
import com.kitchen_manager.service.ElasticsearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch 初始化器（整合拼音搜索配置）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchInitializer implements ApplicationRunner {

    private final ElasticsearchSyncService syncService;
    private final ElasticsearchOperations elasticsearchOperations;
    private final RecipeRepository recipeRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("========================================");
        log.info("开始 Elasticsearch 初始化检查...");

        try {
            // 1. 检查 Elasticsearch 连接
            if (!checkElasticsearchConnection()) {
                log.warn("Elasticsearch 连接失败，搜索功能将降级使用 MySQL");
                return;
            }

            // 2. 检查 recipes 索引是否存在
            IndexCoordinates indexCoordinates = IndexCoordinates.of("recipes");
            IndexOperations indexOps = elasticsearchOperations.indexOps(indexCoordinates);

            boolean indexExists = indexOps.exists();
            log.info("索引 'recipes' 存在: {}", indexExists);

            // 3. 根据索引状态决定是否同步
            if (!indexExists) {
                log.info("索引不存在，开始创建索引并同步数据...");
                createIndexWithPinyinMapping(indexOps);
                syncService.syncAll();
                log.info("索引创建完成，数据同步完成！");
            } else {
                // 检查索引是否需要重建（mapping不匹配）
                if (needIndexRebuild(indexOps)) {
                    log.info("索引映射不匹配，重新创建索引...");
                    recreateIndexWithPinyinMapping(indexOps);
                    syncService.syncAll();
                    log.info("索引重建完成，数据同步完成！");
                } else {
                    // 对比数据库和ES的数据量
                    long dbCount = recipeRepository.countAll();
                    long esCount = elasticsearchOperations.count(Query.findAll(), RecipeDocument.class);

                    log.info("数据库记录数: {}, ES记录数: {}", dbCount, esCount);

                    if (dbCount != esCount) {
                        log.info("数据量不一致，开始同步数据...");
                        syncService.syncAll();
                        log.info("数据同步完成！");
                    } else {
                        log.info("Elasticsearch 数据已是最新，跳过同步");
                    }
                }
            }

            log.info("Elasticsearch 初始化检查完成");
            log.info("========================================");

        } catch (Exception e) {
            log.error("========================================", e);
            log.error("Elasticsearch 初始化失败: {}", e.getMessage());
            log.error("搜索功能将降级使用 MySQL");
            log.error("========================================");

            // 根据错误类型提供建议
            if (e.getMessage().contains("Connection refused")) {
                log.warn("建议: 请确保 Elasticsearch 服务已启动并运行在 localhost:9200");
            } else if (e.getMessage().contains("media_type_header_exception")) {
                log.warn("建议: Elasticsearch 版本不兼容，请确认使用正确版本");
            }
        }
    }

    /**
     * 创建索引并设置拼音搜索Mapping
     */
    private void createIndexWithPinyinMapping(IndexOperations indexOps) {
        // 定义索引设置（包含拼音分析器）
        Map<String, Object> settings = new HashMap<>();

        String analysisSettings = """
        {
          "analysis": {
            "analyzer": {
              "pinyin_analyzer": {
                "tokenizer": "ik_max_word",
                "filter": ["pinyin_filter"]
              }
            },
            "filter": {
              "pinyin_filter": {
                "type": "pinyin",
                "keep_first_letter": true,
                "keep_full_pinyin": true,
                "keep_original": true,
                "limit_first_letter_length": 16,
                "lowercase": true
              }
            }
          }
        }
        """;

        // 使用 IndexOperations 创建索引
        indexOps.create(Document.parse(analysisSettings));

        // 创建自定义映射
        String mappings = """
        {
          "properties": {
            "id": {
              "type": "keyword"
            },
            "name": {
              "type": "text",
              "analyzer": "pinyin_analyzer",
              "search_analyzer": "ik_smart"
            },
            "description": {
              "type": "text",
              "analyzer": "ik_max_word",
              "search_analyzer": "ik_smart"
            },
            "ingredients": {
              "type": "text",
              "analyzer": "pinyin_analyzer",
              "search_analyzer": "ik_smart"
            },
            "popularity": {
              "type": "integer"
            },
            "createdAt": {
              "type": "date",
              "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
            }
          }
        }
        """;

        indexOps.putMapping(Document.parse(mappings));
        log.info("索引 'recipes' 创建完成，拼音搜索Mapping已设置");
    }

    /**
     * 重建索引（删除并重新创建）
     */
    private void recreateIndexWithPinyinMapping(IndexOperations indexOps) {
        log.warn("删除旧索引 'recipes'...");
        indexOps.delete();

        log.info("重新创建索引 'recipes'...");
        createIndexWithPinyinMapping(indexOps);
    }

    /**
     * 检查索引是否需要重建
     * 现在需要检查是否包含拼音分析器
     */
    private boolean needIndexRebuild(IndexOperations indexOps) {
        try {
            Document currentMapping = (Document) indexOps.getMapping();

            // 检查是否包含拼音分析器配置
            String mappingJson = currentMapping.toJson();
            boolean hasPinyinAnalyzer = mappingJson.contains("pinyin_analyzer");
            boolean hasPinyinFilter = mappingJson.contains("pinyin_filter");

            if (!hasPinyinAnalyzer || !hasPinyinFilter) {
                log.warn("索引映射缺少拼音搜索配置");
                return true;
            }

            // 检查关键字段
            boolean hasNameField = mappingJson.contains("\"name\"");
            boolean hasIngredientsField = mappingJson.contains("\"ingredients\"");

            if (!hasNameField || !hasIngredientsField) {
                log.warn("索引映射缺少关键字段");
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("检查索引映射时出错，假定需要重建: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 检查 Elasticsearch 连接是否正常
     */
    private boolean checkElasticsearchConnection() {
        try {
            elasticsearchOperations.indexOps(IndexCoordinates.of("_all")).exists();
            log.info("Elasticsearch 连接正常");
            return true;
        } catch (Exception e) {
            log.error("Elasticsearch 连接失败: {}", e.getMessage());
            return false;
        }
    }
}