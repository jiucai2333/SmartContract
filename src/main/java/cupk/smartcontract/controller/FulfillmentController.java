package cupk.smartcontract.controller;

import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.service.FulfillmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping("/fulfillment-plans")
    public List<FulfillmentPlan> listFulfillmentPlans() {
        return fulfillmentService.listPlans();
    }

    @PutMapping("/fulfillment-plans/{planId}/status")
    public FulfillmentPlan updateStatus(@PathVariable Long planId,
                                        @RequestBody Map<String, String> body) {
        return fulfillmentService.updateStatus(planId, body.get("status"));
    }
}
