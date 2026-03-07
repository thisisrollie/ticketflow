package com.rolliedev.ticketflow.mapper;

public interface Mapper<F, T> {

    T map(F object);
}
