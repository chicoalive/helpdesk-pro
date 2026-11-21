package com.helpdeskpro.backend.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
public record UserStateRequestDTO(@NotNull Boolean active, @NotNull @PositiveOrZero Long expectedVersion) {}
