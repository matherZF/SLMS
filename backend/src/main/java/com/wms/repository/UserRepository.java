package com.wms.repository;

import com.wms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByRole(String role);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    /** 用户名/姓名组合查询：非空条件 AND 组合，空条件不过滤（原 OR 派生查询空串 like '%%' 会命中全部） */
    @Query("select u from User u where (:username = '' or u.username like concat('%', :username, '%')) and (:displayName = '' or u.displayName like concat('%', :displayName, '%'))")
    Page<User> searchByUsernameAndDisplayName(String username, String displayName, Pageable pageable);
}
