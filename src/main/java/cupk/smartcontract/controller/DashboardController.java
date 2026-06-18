package cupk.smartcontract.controller;

import cupk.smartcontract.dto.DashboardVO;
import cupk.smartcontract.service.ContractManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final ContractManagementService contractManagementService;

    public DashboardController(ContractManagementService contractManagementService) {
        this.contractManagementService = contractManagementService;
    }

    @GetMapping("/dashboard")
    public DashboardVO getDashboardSummary() {
        return contractManagementService.dashboardSummary();
    }
}
