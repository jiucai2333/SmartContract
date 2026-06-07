package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.dto.CounterpartyVO;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.mapper.ContractMainMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CounterpartyService {
    private final ContractMainMapper contractMapper;

    public CounterpartyService(ContractMainMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    public List<CounterpartyVO> search(String keyword, String type) {
        return contractMapper.selectList(new LambdaQueryWrapper<ContractMain>()
                        .like(StringUtils.hasText(keyword), ContractMain::getCounterparty, keyword)
                        .eq(StringUtils.hasText(type), ContractMain::getType, type)
                        .orderByAsc(ContractMain::getCounterparty))
                .stream()
                .filter(contract -> StringUtils.hasText(contract.getCounterparty()))
                .collect(Collectors.groupingBy(
                        ContractMain::getCounterparty,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new CounterpartyVO(
                        entry.getKey(),
                        entry.getValue().get(0).getType(),
                        entry.getValue().size()))
                .limit(100)
                .toList();
    }
}
