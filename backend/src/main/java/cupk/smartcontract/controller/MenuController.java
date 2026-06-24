package cupk.smartcontract.controller;

import cupk.smartcontract.common.Result;
import cupk.smartcontract.dto.MenuItem;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.service.MenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security")
public class MenuController {
    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/menus")
    public Result menus() {
        return Result.success(menuService.visibleMenus(SecurityContext.roleCode()));
    }
}
