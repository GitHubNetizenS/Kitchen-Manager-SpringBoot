package com.kitchen_manager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            boolean isMade = uploadDir.mkdirs();

            System.out.println(isMade);
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = null;
        if (originalFilename != null) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = "avatar_" + userId + "_" + System.currentTimeMillis() + extension;

        // 保存文件
        Path filePath = Paths.get(UPLOAD_DIR + uniqueFilename);
        Files.write(filePath, file.getBytes());

        // 返回访问URL
        return "/uploads/" + uniqueFilename;
    }
}