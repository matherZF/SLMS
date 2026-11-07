package com.wms.repository;

import com.wms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByRole(String role);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Page<User> findByUsernameContainingOrDisplayNameContaining(String username, String displayName, Pageable pageable);
    Page<User> findByUsernameContaining(String username, Pageable pageable);
    Page<User> findByDisplayNameContaining(String displayName, Pageable pageable);
}
