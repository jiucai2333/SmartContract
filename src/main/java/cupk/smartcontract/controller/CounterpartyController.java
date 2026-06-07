package cupk.smartcontract.controller;

import cupk.smartcontract.dto.CounterpartyVO;
import cupk.smartcontract.service.CounterpartyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/counterparties")
public class CounterpartyController {
    private final CounterpartyService service;

    public CounterpartyController(CounterpartyService service) {
        this.service = service;
    }

    @GetMapping
    public List<CounterpartyVO> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String type) {
        return service.search(keyword, type);
    }
}
