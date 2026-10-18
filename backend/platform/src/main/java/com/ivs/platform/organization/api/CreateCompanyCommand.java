package com.ivs.platform.organization.api;
import jakarta.validation.constraints.NotBlank;
public record CreateCompanyCommand(@NotBlank String code, @NotBlank String name) {}
