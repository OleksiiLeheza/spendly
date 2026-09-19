package com.spendly.userservice.repository;

import com.spendly.userservice.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE u.keycloakId = :keycloakId AND u.deletedAt IS NULL")
    Optional<User> findActiveByKeycloakId(@Param("keycloakId") UUID keycloakId);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmailIgnoreCase(@Param("email") String email);
}
