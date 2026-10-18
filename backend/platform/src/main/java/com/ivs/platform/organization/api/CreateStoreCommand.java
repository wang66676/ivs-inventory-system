package com.ivs.platform.organization.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record CreateStoreCommand(@NotNull UUID companyId, @NotBlank String code, @NotBlank String name) {}
