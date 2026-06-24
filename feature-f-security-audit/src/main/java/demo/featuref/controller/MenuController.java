package demo.featuref.controller;

import demo.featuref.common.Result;
import demo.featuref.dto.MenuItem;
import demo.featuref.security.SecurityContext;
import demo.featuref.service.MenuService;
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
    public Result<List<MenuItem>> menus() {
        return Result.success(menuService.visibleMenus(SecurityContext.roleCode()));
    }
}
