package com.pillapago.tenant.repository;

import com.pillapago.tenant.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailHash(String emailHash);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailHash(String emailHash);

    boolean existsByEmailIgnoreCase(String email);
}
