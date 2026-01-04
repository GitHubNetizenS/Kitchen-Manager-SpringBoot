package com.kitchen_manager.repository;

import com.kitchen_manager.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;


@Repository
public interface UserTagRepository extends JpaRepository<UserTag, Integer> {
    List<UserTag> findByUserId(Integer userId);

    @Query("SELECT ut.tagId FROM UserTag ut WHERE ut.userId = :userId")
    List<Integer> findTagIdsByUserId(@Param("userId") Integer userId);

    @Modifying
    @Query("DELETE FROM UserTag ut WHERE ut.userId = :userId AND ut.tagId IN " +
            "(SELECT t.id FROM Tag t WHERE t.category = :category)")
    void deleteByUserIdAndCategory(@Param("userId") Integer userId, @Param("category") String category);
}
