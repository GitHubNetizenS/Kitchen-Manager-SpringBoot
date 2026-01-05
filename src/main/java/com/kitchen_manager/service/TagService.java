package com.kitchen_manager.service;

import com.kitchen_manager.entity.*;
import com.kitchen_manager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;
    private final UserTagRepository userTagRepository;

    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    public Map<String, List<Integer>> getUserTags(Integer userId) {
        List<UserTag> userTags = userTagRepository.findByUserId(userId);
        Map<String, List<Integer>> result = new HashMap<>();

        for (UserTag ut : userTags) {
            tagRepository.findById(ut.getTagId()).ifPresent(tag -> result.computeIfAbsent(tag.getCategory(), k -> new ArrayList<>())
                    .add(ut.getTagId()));
        }

        return result;
    }

    @Transactional
    public void saveUserTags(Integer userId, String category, List<Integer> tagIds) {
        // 删除该分类下的旧标签
        userTagRepository.deleteByUserIdAndCategory(userId, category);

        // 插入新标签
        for (Integer tagId : tagIds) {
            UserTag userTag = new UserTag();
            userTag.setUserId(userId);
            userTag.setTagId(tagId);
            userTagRepository.save(userTag);
        }
    }
}