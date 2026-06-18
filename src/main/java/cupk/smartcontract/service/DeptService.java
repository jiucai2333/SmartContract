package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.entity.DeptInfo;
import cupk.smartcontract.mapper.DeptInfoMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeptService {
    private final DeptInfoMapper mapper;

    public DeptService(DeptInfoMapper mapper) {
        this.mapper = mapper;
    }

    public List<DeptInfo> list() {
        return mapper.selectList(new LambdaQueryWrapper<DeptInfo>()
                .orderByAsc(DeptInfo::getLevel)
                .orderByAsc(DeptInfo::getDeptId));
    }
}
