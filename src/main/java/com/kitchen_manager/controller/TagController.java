package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.dto.*;
import com.kitchen_manager.repository.*;
import com.kitchen_manager.entity.*;
import com.kitchen_manager.service.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

/**
 * 标签相关接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;
    private final Gson gson = new Gson();

    /**
     * 获取所有标签
     * GET /api/tags
     */
    @GetMapping("/tags")
    public ApiResponse<List<Tag>> getAllTags() {
        try {
            List<Tag> tags = tagService.getAllTags();
            return ApiResponse.success("获取成功", tags);
        } catch (Exception e) {
            return ApiResponse.error("数据库错误: " + e.getMessage());
        }
    }

    /**
     * 获取用户标签
     * GET /api/user/tags
     */
    @GetMapping("/user/tags")
    public ApiResponse<Map<String, List<Integer>>> getUserTags(
            @RequestParam("user_id") Integer userId) {

        try {
            Map<String, List<Integer>> userTags = tagService.getUserTags(userId);
            return ApiResponse.success("获取成功", userTags);
        } catch (Exception e) {
            return ApiResponse.error("数据库错误: " + e.getMessage());
        }
    }

    /**
     * 保存用户标签
     * POST /api/user/tags
     */
    @PostMapping("/user/tags")
    public ApiResponse<Void> saveUserTags(
            @RequestParam("user_id") Integer userId,
            @RequestParam("category") String category,
            @RequestParam("tag_ids") String tagIdsJson) {

        try {
            List<Integer> tagIds = gson.fromJson(tagIdsJson,
                    new TypeToken<List<Integer>>(){}.getType());

            tagService.saveUserTags(userId, category, tagIds);
            return ApiResponse.success("保存成功", null);
        } catch (Exception e) {
            return ApiResponse.error("数据库错误: " + e.getMessage());
        }
    }
}

