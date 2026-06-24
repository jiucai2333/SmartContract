package cupk.smartcontract.service;

import cupk.smartcontract.dto.MenuItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MenuService {
    private static final List<MenuItem> ITEMS = List.of(
            new MenuItem("dashboard", "/", "工作台", null, Set.of()),
            new MenuItem("draft", "/draft", "合同起草", "drafting",
                    Set.of("USER", "DEPT_LEADER", "LEGAL", "ADMIN")),
            new MenuItem("templates", "/templates", "模板管理", "drafting", Set.of()),
            new MenuItem("approval", "/approval", "审批中心", null,
                    Set.of("DEPT_LEADER", "LEGAL", "EXECUTIVE", "ADMIN")),
            new MenuItem("ledger", "/ledger", "合同台账", "contract", Set.of()),
            new MenuItem("signature", "/signature", "电子签章", "contract",
                    Set.of("LEGAL", "DEPT_LEADER", "ADMIN")),
            new MenuItem("seal", "/seal", "用印登记", "contract",
                    Set.of("LEGAL", "DEPT_LEADER", "ADMIN")),
            new MenuItem("archive", "/archive", "归档确认", "contract",
                    Set.of("LEGAL", "DEPT_LEADER", "ADMIN")),
            new MenuItem("fulfillment", "/fulfillment", "履约预警", null, Set.of()),
            new MenuItem("users", "/users", "用户管理", null, Set.of("ADMIN")),
            new MenuItem("audit", "/audit", "审计日志", null, Set.of("ADMIN"))
    );

    public List<MenuItem> visibleMenus(String roleCode) {
        String role = roleCode == null || roleCode.isBlank()
                ? "USER"
                : roleCode.trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(role)) {
            return ITEMS;
        }
        return ITEMS.stream()
                .filter(item -> item.allows(role))
                .toList();
    }
}
