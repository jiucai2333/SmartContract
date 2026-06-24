package cupk.smartcontract.service;

import cupk.smartcontract.dto.AuthUserVO;
import cupk.smartcontract.event.ContractArchivedEvent;
import cupk.smartcontract.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FulfillmentArchiveListener {
    private static final Logger log = LoggerFactory.getLogger(FulfillmentArchiveListener.class);

    private final FulfillmentService fulfillmentService;

    public FulfillmentArchiveListener(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @Async("fulfillmentTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContractArchived(ContractArchivedEvent event) {
        SecurityContext.set(AuthUserVO.of(0L, "system", null, "ADMIN", "ALL"));
        try {
            fulfillmentService.extractPlans(event.contractId());
        } catch (Exception ex) {
            log.warn("Auto fulfillment node extraction after archive failed. contractId={}, versionId={}, reason={}",
                    event.contractId(), event.versionId(), ex.getMessage());
        } finally {
            SecurityContext.clear();
        }
    }
}
