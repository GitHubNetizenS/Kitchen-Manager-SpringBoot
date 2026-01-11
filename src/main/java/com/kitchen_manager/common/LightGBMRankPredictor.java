package com.kitchen_manager.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 调用Python预测代码
 */
public class LightGBMRankPredictor {
    private final String        pythonPath;                                // Python的可执行路径，例如“python”。
    private final String        pythonScriptPath;
    private final String        modelPath;                                 // 模型文件路径，例如"lightgbm_rank_model.txt"
    private final ObjectMapper  objectMapper = new ObjectMapper();

    public LightGBMRankPredictor(String pythonPath, String modelPath) {
        this.pythonPath = pythonPath;
        this.modelPath = modelPath;
        this.pythonScriptPath = "rank_predictor.py";
    }

    public LightGBMRankPredictor(String pythonPath, String pythonScriptPath, String modelPath) {
        this.pythonPath = pythonPath;
        this.pythonScriptPath = pythonScriptPath;
        this.modelPath = modelPath;
    }

    /**
     * 通过LightGBM Rank算法计算菜谱得分
     * @param featureList 特征值列表
     * @return 预测值列表
     * @throws IOException 输入输出异常
     * @throws InterruptedException 中断异常
     */
    public List<Double> predictScores(List<List<Double>> featureList) throws IOException, InterruptedException {
        if(null==featureList || featureList.isEmpty()) {

            return new ArrayList<>();
        }
        // 将特征列表转换为JSON字符串。
        File tempFile = File.createTempFile("features_", ".json");

        objectMapper.writeValue(tempFile, featureList);

        // 打印调试信息。
        System.out.println("调试信息：");
        System.out.println("Java端：准备调用Python脚本。");
        System.out.println("Java端：Python路径：" + pythonPath + "。");
        System.out.println("Python脚本路径：" + pythonScriptPath + "。");
        System.out.println("Java端：模型路径：" + modelPath + "。");
        // 检查文件是否存在
        File scriptFile = new File(pythonScriptPath);
        File modelFile = new File(modelPath);
        String absoluteModelPath = modelFile.getAbsolutePath();

        // ========= 新增：在脚本同目录创建副本 =========
        if (scriptFile.getParent() != null) {
            File localCopy = new File(scriptFile.getParent(), "features_copy.json");
            try {
                Files.copy(tempFile.toPath(), localCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("特征文件副本已创建在: " + localCopy.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("创建本地副本失败: " + e.getMessage());
            }
        }
        // ========================================

        System.out.println("脚本文件存在：" + scriptFile.exists() + "，路径：" + scriptFile.getAbsolutePath());
        System.out.println("模型文件存在：" + modelFile.exists() + "，路径：" + modelFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(
                pythonPath,
                "rank_predictor.py",
                absoluteModelPath,
                tempFile.getAbsolutePath()
        );

        pb.directory(new File(scriptFile.getParent()));
        // 分别捕获标准输出和错误输出。
        pb.redirectErrorStream(true); // 不合并

        Process process = pb.start();
        // 读取Python输出
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;

            while((line=reader.readLine()) != null) {
                System.out.println("Python输出:" + line);  // 实时打印Python输出
                if (line.startsWith("DEBUG:") || line.startsWith("ERROR:") || line.startsWith("python:")) {
                    errorOutput.append(line).append("\n");
                } else {
                    output.append(line);
                }
            }
        }

        int exitCode = process.waitFor();

        System.out.println("Python退出码：" + exitCode);
        if(exitCode != 0) {
            System.err.println("Python错误输出：\n" + errorOutput);
            throw new RuntimeException("Python脚本执行失败！错误码：" + exitCode + "\n错误信息：\n" + errorOutput);
        }
        // 解析JSON输出
        String result = output.toString();

        if(result.trim().isEmpty()) {
            throw new RuntimeException("Python脚本没有返回结果。");
        }

        tempFile.delete();

        return objectMapper.readValue(result, new TypeReference<>() {});
    }
}