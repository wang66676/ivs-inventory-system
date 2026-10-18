package com.ivs.platform.organization.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record CreateTeamCommand(@NotNull UUID companyId, @NotNull UUID storeId, @NotBlank String code, @NotBlank String name) {}
