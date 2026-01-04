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
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    Optional<User> findByPhone(String phone);
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);

    @Query("SELECT u FROM User u WHERE (u.username = :loginId OR u.phone = :loginId) AND u.password = :password")
    Optional<User> authenticate(@Param("loginId") String loginId, @Param("password") String password);
}