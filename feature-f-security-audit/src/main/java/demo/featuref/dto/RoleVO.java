package demo.featuref.dto;

import demo.featuref.common.DataScope;
import demo.featuref.common.RoleCode;

public record RoleVO(RoleCode roleCode, String roleName, DataScope dataScope) {
}
