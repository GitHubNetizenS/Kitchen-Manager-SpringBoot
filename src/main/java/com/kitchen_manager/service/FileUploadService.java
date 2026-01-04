package com.kitchen_manager.service;

import com.kitchen_manager.entity.*;
import com.kitchen_manager.dto.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private static final String UPLOAD_DIR = "uploads/";

    public String uploadAvatar(Integer userId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("文件为空");
        }

        // 创建上传目录
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String uniqueFilename = "avatar_" + userId + "_" + System.currentTimeMillis() + extension;

        // 保存文件
        Path filePath = Paths.get(UPLOAD_DIR + uniqueFilename);
        Files.write(filePath, file.getBytes());

        // 返回访问URL
        return "http://localhost:8080/uploads/" + uniqueFilename;
    }
}