package cupk.smartcontract.service;

import cupk.smartcontract.config.SignatureProperties;
import cupk.smartcontract.dto.*;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.SealRecord;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.SealRecordMapper;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.service.signature.SignatureProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 签章编排服务。
 * 根据 signature.provider 配置选择合适的 SignatureProvider 实现，
 * 统一管理签章请求、状态查询、回调处理和验证。
 */
@Service
public class SignatureService {

    private static final Logger log = LoggerFactory.getLogger(SignatureService.class);

    private final Map<String, SignatureProvider> providers;
    private final SignatureProperties properties;
    private final SealRecordMapper sealRecordMapper;
    private final ContractMainMapper contractMainMapper;
    private final BlockchainService blockchainService;

    public SignatureService(Map<String, SignatureProvider> providers,
                            SignatureProperties properties,
                            SealRecordMapper sealRecordMapper,
                            ContractMainMapper contractMainMapper,
                            BlockchainService blockchainService) {
        this.providers = providers;
        this.properties = properties;
        this.sealRecordMapper = sealRecordMapper;
        this.contractMainMapper = contractMainMapper;
        this.blockchainService = blockchainService;
    }

    private SignatureProvider resolveProvider() {
        String name = properties.resolvedProvider();
        SignatureProvider provider = providers.get(name + "SignatureProvider");
        if (provider == null) {
            log.warn("未找到签章提供者 '{}'，回退到 localSignatureProvider", name);
            provider = providers.get("localSignatureProvider");
        }
        if (provider == null) {
            throw new IllegalStateException("没有可用的签章提供者");
        }
        return provider;
    }

    /**
     * 发起电子签章请求。
     */
    public SignatureResponse requestSignature(SignatureRequest request) {
        SignatureProvider provider = resolveProvider();
        SignatureResponse response = provider.sign(request);
        if (response.isSuccess() || "PENDING".equalsIgnoreCase(response.status())) {
            saveSealRecord(request, response);
            // 更新合同状态为已签章
            ContractMain contract = contractMainMapper.selectById(request.contractId());
            if (contract != null && "APPROVED".equals(contract.getStatus())) {
                contract.setStatus("SIGNING");
                contract.setUpdatedAt(LocalDateTime.now());
                contractMainMapper.updateById(contract);
                log.info("合同状态已更新为 SIGNING: contractId={}", request.contractId());
            }
            // 区块链存证锚定
            try {
                blockchainService.anchorToChain(request.contractId(), request.versionId(), "SEAL", "电子签章锚定");
            } catch (Exception e) {
                log.warn("区块链锚定失败(非阻塞): contractId={}", request.contractId(), e);
            }
        }
        return response;
    }

    /**
     * 查询签章状态，同步更新 SealRecord。
     */
    public SignatureResponse getSignatureStatus(String transactionId) {
        SignatureProvider provider = resolveProvider();
        SignatureResponse response = provider.queryStatus(transactionId);
        if (response != null && response.isSuccess()) {
            SealRecord record = sealRecordMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SealRecord>()
                            .eq(SealRecord::getTransactionId, transactionId));
            if (record != null && !"SIGNED".equalsIgnoreCase(record.getSealStatus())) {
                record.setSealStatus("SIGNED");
                if (response.signedFileUrl() != null) {
                    record.setSignatureData("{\"signUrl\":\"" + response.signedFileUrl() + "\"}");
                }
                record.setUpdatedAt(LocalDateTime.now());
                sealRecordMapper.updateById(record);
            }
        }
        return response;
    }

    /**
     * 处理第三方签章平台异步回调。
     */
    @Transactional
    public void handleCallback(SignatureCallbackRequest callback) {
        SealRecord record = sealRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SealRecord>()
                        .eq(SealRecord::getTransactionId, callback.transactionId()));
        if (record == null) {
            log.warn("回调收到未知事务 ID: {}", callback.transactionId());
            return;
        }
        if ("SIGNED".equalsIgnoreCase(callback.status())) {
            record.setSealStatus("SIGNED");
            if (callback.signedFileUrl() != null) {
                record.setSignatureData("{\"signUrl\":\"" + callback.signedFileUrl() + "\"}");
            }
            record.setSealTime(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            sealRecordMapper.updateById(record);
            log.info("签章回调处理成功: transactionId={}, status={}", callback.transactionId(), callback.status());
        } else {
            log.warn("签章回调状态非 SIGNED: transactionId={}, status={}", callback.transactionId(), callback.status());
        }
    }

    /**
     * 验证签名有效性。
     */
    public VerificationResult verifySignature(VerificationRequest request) {
        SignatureProvider provider = resolveProvider();
        return provider.verify(request);
    }

    /**
     * 创建签章记录（电子签章模式）。
     */
    @Transactional
    public SealRecord saveSealRecord(SignatureRequest request, SignatureResponse response) {
        SealRecord record = new SealRecord();
        record.setContractId(request.contractId());
        record.setVersionId(request.versionId());
        record.setFileId(request.fileId());
        record.setSealStatus("ELECTRONIC");
        record.setSignatureProvider(properties.resolvedProvider());
        record.setTransactionId(response.transactionId());
        if (response.signedFileUrl() != null) {
            record.setSignatureData("{\"signUrl\":\"" + response.signedFileUrl() + "\"}");
        }
        record.setSealTime(LocalDateTime.now());
        record.setOperatorId(SecurityContext.userId());
        record.setCreatedBy(SecurityContext.userId() != null
                ? "SIGN_" + SecurityContext.userId() : "SIGN_SYSTEM");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeleted(0);
        record.setVersion(1);
        sealRecordMapper.insert(record);
        log.info("电子签章记录已创建: sealId={}, transactionId={}", record.getSealId(), response.transactionId());
        return record;
    }
}
