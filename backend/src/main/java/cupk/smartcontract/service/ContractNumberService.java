package cupk.smartcontract.service;

import cupk.smartcontract.mapper.ContractMainMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContractNumberService {

    private final ContractMainMapper contractMapper;

    public ContractNumberService(ContractMainMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    public synchronized <T> T withNextNumber(Function<String, T> insertAction) {
        String contractNo = nextNumber(LocalDate.now().getYear());
        return insertAction.apply(contractNo);
    }

    String nextNumber(int year) {
        String prefix = "HT-" + year + "-";
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d{3})$");
        int maxSequence = contractMapper.selectContractNumbersByPrefix(prefix)
                .stream()
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElse(0);
        return "%s%03d".formatted(prefix, maxSequence + 1);
    }
}
