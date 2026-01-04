package com.kitchen_manager.controller;

import com.kitchen_manager.common.ApiResponse;
import com.kitchen_manager.dto.*;
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
 * 用户相关接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final FileUploadService fileUploadService;

    /**
     * 统一用户操作接口 (注册、登录、更新资料)
     * POST /api/user
     */
    @PostMapping("/user")
    public ApiResponse<?> userAction(
            @RequestParam("action") String action,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "login_id", required = false) String loginId,
            @RequestParam(value = "user_id", required = false) Integer userId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "avatar_url", required = false) String avatarUrl) {

        try {
            switch (action) {
                case "register":
                    User registeredUser = userService.register(username, password, phone);
                    return ApiResponse.success("注册成功", userService.toResponse(registeredUser));

                case "login":
                    User loginUser = userService.login(loginId, password);
                    return ApiResponse.success("登录成功", userService.toResponse(loginUser));

                case "update_profile":
                    UserUpdateRequest updateRequest = new UserUpdateRequest();
                    updateRequest.setUserId(userId);
                    updateRequest.setUsername(username);
                    updateRequest.setPhone(phone);
                    updateRequest.setTitle(title);
                    updateRequest.setAvatarUrl(avatarUrl);

                    User updatedUser = userService.updateProfile(updateRequest);
                    return ApiResponse.success("用户信息更新成功", userService.toResponse(updatedUser));

                default:
                    return ApiResponse.error(400, "无效的操作类型: " + action);
            }
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误: " + e.getMessage());
        }
    }

    /**
     * 获取用户资料
     * POST /api/user/profile
     */
    @PostMapping("/user/profile")
    public ApiResponse<UserResponse> getUserProfile(@RequestParam("user_id") Integer userId) {
        try {
            User user = userService.getUserById(userId);
            return ApiResponse.success("获取用户信息成功", userService.toResponse(user));
        } catch (Exception e) {
            return ApiResponse.error(404, "未找到该用户");
        }
    }

    /**
     * 上传头像
     * POST /api/upload
     */
    @PostMapping("/upload")
    public ApiResponse<String> uploadAvatar(
            @RequestPart("user_id") String userIdStr,
            @RequestPart("avatar") MultipartFile file) {

        try {
            Integer userId = Integer.parseInt(userIdStr);
            String avatarUrl = fileUploadService.uploadAvatar(userId, file);

            // 更新用户头像URL
            UserUpdateRequest request = new UserUpdateRequest();
            request.setUserId(userId);
            User user = userService.getUserById(userId);
            request.setUsername(user.getUsername());
            request.setPhone(user.getPhone());
            request.setAvatarUrl(avatarUrl);
            userService.updateProfile(request);

            return ApiResponse.success("头像上传成功", avatarUrl);
        } catch (Exception e) {
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }
}
