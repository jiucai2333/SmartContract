package cupk.smartcontract.service;

import cupk.smartcontract.mapper.ContractMainMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContractNumberServiceTest {

    @Test
    void startsAt001AndIncrementsOnlyThreeDigitNumbersForTheYear() {
        ContractMainMapper mapper = mock(ContractMainMapper.class);
        when(mapper.selectContractNumbersByPrefix("HT-2026-")).thenReturn(List.of(
                "HT-2026-001",
                "HT-2026-009",
                "HT-2026-41077",
                "HT-2025-999",
                "OTHER-2026-010"
        ));

        ContractNumberService service = new ContractNumberService(mapper);

        assertThat(service.nextNumber(2026)).isEqualTo("HT-2026-010");
    }

    @Test
    void startsAt001WhenNoStandardNumberExists() {
        ContractMainMapper mapper = mock(ContractMainMapper.class);
        when(mapper.selectContractNumbersByPrefix("HT-2026-"))
                .thenReturn(List.of("HT-2026-41077"));

        assertThat(new ContractNumberService(mapper).nextNumber(2026))
                .isEqualTo("HT-2026-001");
    }

    @Test
    void deletedHistoricalNumbersStillAdvanceTheSequence() {
        ContractMainMapper mapper = mock(ContractMainMapper.class);
        when(mapper.selectContractNumbersByPrefix("HT-2026-")).thenReturn(List.of(
                "HT-2026-020",
                "HT-2026-021"
        ));

        assertThat(new ContractNumberService(mapper).nextNumber(2026))
                .isEqualTo("HT-2026-022");
    }

}
