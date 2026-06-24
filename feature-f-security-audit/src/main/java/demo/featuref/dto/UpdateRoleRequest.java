package demo.featuref.dto;

import demo.featuref.common.RoleCode;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull RoleCode roleCode) {
}
