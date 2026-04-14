package com.kitchen_manager.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class LightGBMRankHttpPredictor {
    private final String serviceUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LightGBMRankHttpPredictor(String serviceUrl) {
        this.serviceUrl = serviceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<Double> predictScores(List<List<Double>> featureList, List<Integer> sessionSeq, List<Integer> candidateIds) throws IOException, InterruptedException {
        if (featureList == null || featureList.isEmpty()) {
            return List.of();
        }

        // 构造请求body
        Map<String, Object> requestBody = Map.of(
                "features", featureList,
                "session_seq", sessionSeq,
                "candidate_ids", candidateIds);
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // 发送HTTP POST请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serviceUrl + "/predict"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP请求失败: " + response.statusCode() + ", " + response.body());
        }

        // 解析响应
        Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);

        @SuppressWarnings("unchecked")
        List<Double> scores = (List<Double>) responseMap.get("scores");

        Integer elapsedMs = (Integer) responseMap.get("elapsed_ms");
        System.out.println("预测耗时: " + elapsedMs + "ms");

        return scores;
    }
}