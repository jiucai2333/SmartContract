package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cupk.smartcontract.entity.OperationLog;
import cupk.smartcontract.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SecurityAuditService {

    private final OperationLogMapper operationLogMapper;

    public SecurityAuditService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public void record(Long userId, String operation, String targetType, Long targetId,
                       String ip, String result) {
        OperationLog log = new OperationLog();
        log.setUserId(userId != null ? userId : 0L);
        log.setOperation(operation);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp(StringUtils.hasText(ip) ? ip : "unknown");
        log.setResult(result);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    public IPage<OperationLog> search(long page, long size, Long userId, String operation,
                                      String result, LocalDateTime start, LocalDateTime end) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        LambdaQueryWrapper<OperationLog> query = new LambdaQueryWrapper<OperationLog>()
                .eq(userId != null, OperationLog::getUserId, userId)
                .eq(StringUtils.hasText(operation), OperationLog::getOperation, operation)
                .eq(StringUtils.hasText(result), OperationLog::getResult, result)
                .ge(start != null, OperationLog::getCreatedAt, start)
                .le(end != null, OperationLog::getCreatedAt, end)
                .orderByDesc(OperationLog::getCreatedAt);
        return operationLogMapper.selectPage(new Page<>(safePage, safeSize), query);
    }
}
