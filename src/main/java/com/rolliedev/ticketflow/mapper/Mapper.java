package com.rolliedev.ticketflow.mapper;

public interface Mapper<F, T> {

    T toDto(F entity);
}
