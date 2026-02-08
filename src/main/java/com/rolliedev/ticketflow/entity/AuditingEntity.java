package com.rolliedev.ticketflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class AuditingEntity<T extends Serializable> implements BaseEntity<T> {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
