package demo.featuref.service;

import demo.featuref.common.RoleCode;
import demo.featuref.dto.MenuItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class MenuService {
    private static final List<MenuItem> ITEMS = List.of(
            new MenuItem("dashboard", "/", "Dashboard", null, Set.of()),
            new MenuItem("draft", "/draft", "Contract Draft", "drafting",
                    Set.of(RoleCode.USER, RoleCode.DEPT_LEADER, RoleCode.LEGAL, RoleCode.ADMIN)),
            new MenuItem("templates", "/templates", "Templates", "drafting", Set.of()),
            new MenuItem("approval", "/approval", "Approval Center", null,
                    Set.of(RoleCode.DEPT_LEADER, RoleCode.LEGAL, RoleCode.EXECUTIVE, RoleCode.ADMIN)),
            new MenuItem("ledger", "/ledger", "Contract Ledger", "contract", Set.of()),
            new MenuItem("seal", "/seal", "Seal Register", "contract",
                    Set.of(RoleCode.LEGAL, RoleCode.DEPT_LEADER, RoleCode.ADMIN)),
            new MenuItem("archive", "/archive", "Archive Confirm", "contract",
                    Set.of(RoleCode.LEGAL, RoleCode.DEPT_LEADER, RoleCode.ADMIN)),
            new MenuItem("fulfillment", "/fulfillment", "Fulfillment Warning", null, Set.of()),
            new MenuItem("users", "/users", "User Management", null, Set.of(RoleCode.ADMIN)),
            new MenuItem("audit", "/audit", "Audit Logs", null, Set.of(RoleCode.ADMIN))
    );

    public List<MenuItem> visibleMenus(RoleCode roleCode) {
        RoleCode role = roleCode == null ? RoleCode.USER : roleCode;
        if (role.isAdmin()) {
            return ITEMS;
        }
        return ITEMS.stream()
                .filter(item -> item.allows(role))
                .toList();
    }
}
