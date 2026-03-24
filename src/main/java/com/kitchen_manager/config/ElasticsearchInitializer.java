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
 * Elasticsearch 初始化器
 *
 * 核心修改：mapping 中 name / ingredients 字段的 search_analyzer
 * 由 ik_smart 改为 pinyin_analyzer，让查询侧也能对拼音连写进行 token 拆分。
 *
 * 原来的问题：
 *   索引时  "牛奶" → pinyin_analyzer → tokens: [牛奶, niu, nai, nn]
 *   查询时  "niunai" → ik_smart → token:  [niunai]   ← 匹配不到任何 token
 *
 * 修改后：
 *   查询时  "niunai" → pinyin_analyzer → tokens: [niunai, niu, nai, nn] ← 能匹配 niu/nai
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
            if (!checkElasticsearchConnection()) {
                log.warn("Elasticsearch 连接失败，搜索功能将降级使用 MySQL");
                return;
            }

            IndexCoordinates indexCoordinates = IndexCoordinates.of("recipes");
            IndexOperations indexOps = elasticsearchOperations.indexOps(indexCoordinates);

            boolean indexExists = indexOps.exists();
            log.info("索引 'recipes' 存在: {}", indexExists);

            if (!indexExists) {
                log.info("索引不存在，开始创建索引并同步数据...");
                createIndexWithPinyinMapping(indexOps);
                syncService.syncAll();
                log.info("索引创建完成，数据同步完成！");
            } else {
                if (needIndexRebuild(indexOps)) {
                    log.info("索引映射不匹配，重新创建索引...");
                    recreateIndexWithPinyinMapping(indexOps);
                    syncService.syncAll();
                    log.info("索引重建完成，数据同步完成！");
                } else {
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
            log.error("========================================");
            log.error("Elasticsearch 初始化失败: {}", e.getMessage());
            log.error("搜索功能将降级使用 MySQL");
            log.error("========================================");

            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                log.warn("建议: 请确保 Elasticsearch 服务已启动并运行在 localhost:9200");
            } else if (e.getMessage() != null && e.getMessage().contains("media_type_header_exception")) {
                log.warn("建议: Elasticsearch 版本不兼容，请确认使用正确版本");
            }
        }
    }

    /**
     * 创建索引并设置拼音搜索 Mapping
     *
     * 关键改动：name / ingredients 字段的 search_analyzer 改为 pinyin_analyzer
     * 这样查询侧的拼音连写（如 niunai）也会被拆分为 [niu, nai]，能匹配索引中的 token
     */
    private void createIndexWithPinyinMapping(IndexOperations indexOps) {

        // ① 索引设置：定义 pinyin_analyzer
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
                "keep_joined_full_pinyin": true,
                "limit_first_letter_length": 16,
                "lowercase": true
              }
            }
          }
        }
        """;

        indexOps.create(Document.parse(analysisSettings));

        // ② Mapping：search_analyzer 改为 pinyin_analyzer
        //    这是解决 "niunai" 连写无结果 的核心修改
        //    索引时：ik_max_word 切词 → pinyin_filter 生成拼音/首字母 token
        //    查询时：pinyin_analyzer 同样拆分查询词，与索引 token 对齐
        String mappings = """
        {
          "properties": {
            "recipeId": {
              "type": "integer"
            },
            "name": {
              "type": "text",
              "analyzer": "pinyin_analyzer",
              "search_analyzer": "pinyin_analyzer"
            },
            "ingredients": {
              "type": "text",
              "analyzer": "pinyin_analyzer",
              "search_analyzer": "pinyin_analyzer"
            },
            "taste": {
              "type": "keyword"
            },
            "method": {
              "type": "keyword"
            },
            "difficulty": {
              "type": "keyword"
            },
            "time": {
              "type": "keyword"
            },
            "popularity": {
              "type": "integer"
            },
            "imageUrl": {
              "type": "text",
              "index": false
            }
          }
        }
        """;

        indexOps.putMapping(Document.parse(mappings));
        log.info("索引 'recipes' 创建完成，查询侧拼音分析器已配置");
    }

    private void recreateIndexWithPinyinMapping(IndexOperations indexOps) {
        log.warn("删除旧索引 'recipes'...");
        indexOps.delete();
        log.info("重新创建索引 'recipes'...");
        createIndexWithPinyinMapping(indexOps);
    }

    /**
     * 检查是否需要重建索引
     * 增加对 search_analyzer 是否为 pinyin_analyzer 的检查
     */
    private boolean needIndexRebuild(IndexOperations indexOps) {
        try {
            Document currentMapping = (Document) indexOps.getMapping();
            String mappingJson = currentMapping.toJson();

            boolean hasPinyinAnalyzer    = mappingJson.contains("pinyin_analyzer");
            boolean hasPinyinFilter      = mappingJson.contains("pinyin_filter");
            boolean hasKeepJoined        = mappingJson.contains("keep_joined_full_pinyin");
            boolean hasNameField         = mappingJson.contains("\"name\"");
            boolean hasIngredientsField  = mappingJson.contains("\"ingredients\"");

            if (!hasPinyinAnalyzer || !hasPinyinFilter) {
                log.warn("索引映射缺少拼音搜索配置，需要重建");
                return true;
            }
            if (!hasKeepJoined) {
                // 旧索引缺少 keep_joined_full_pinyin，连写拼音无法工作
                log.warn("索引映射缺少 keep_joined_full_pinyin 配置，需要重建");
                return true;
            }
            if (!hasNameField || !hasIngredientsField) {
                log.warn("索引映射缺少关键字段，需要重建");
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("检查索引映射时出错，假定需要重建: {}", e.getMessage());
            return true;
        }
    }

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