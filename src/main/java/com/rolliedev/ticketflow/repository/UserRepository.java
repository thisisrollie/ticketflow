package com.rolliedev.ticketflow.repository;

import com.rolliedev.ticketflow.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
}
