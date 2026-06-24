package cupk.smartcontract.service;

import cupk.smartcontract.security.SensitiveDataMasker;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * F module helper for display/export masking.
 * Page rendering and export code should call the same masking path.
 */
@Service
public class MaskedOutputService {
    private final SensitiveDataMasker sensitiveDataMasker;

    public MaskedOutputService(SensitiveDataMasker sensitiveDataMasker) {
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    public String forDisplay(Object value) {
        return sensitiveDataMasker.maskForDisplay(Objects.toString(value, ""));
    }

    public String forExport(Object value) {
        return sensitiveDataMasker.maskForDisplay(Objects.toString(value, ""));
    }
}
