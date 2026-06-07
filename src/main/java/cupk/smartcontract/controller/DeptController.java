package cupk.smartcontract.controller;

import cupk.smartcontract.entity.DeptInfo;
import cupk.smartcontract.service.DeptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DeptController {
    private final DeptService service;

    public DeptController(DeptService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeptInfo> list() {
        return service.list();
    }
}
