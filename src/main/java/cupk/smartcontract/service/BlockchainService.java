package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cupk.smartcontract.entity.ArchiveRecord;
import cupk.smartcontract.entity.BlockchainRecord;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.SealRecord;
import cupk.smartcontract.mapper.ArchiveRecordMapper;
import cupk.smartcontract.mapper.BlockchainRecordMapper;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.SealRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 区块链防篡改服务。
 * 基于 SHA-256 哈希链实现防篡改存证，将合同签章数据锚定到哈希链中。
 * 通过重新计算哈希可验证数据完整性。
 */
@Service
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    private final BlockchainRecordMapper blockchainRecordMapper;
    private final ContractMainMapper contractMainMapper;
    private final SealRecordMapper sealRecordMapper;
    private final ArchiveRecordMapper archiveRecordMapper;
    private final ObjectMapper objectMapper;

    public BlockchainService(BlockchainRecordMapper blockchainRecordMapper,
                             ContractMainMapper contractMainMapper,
                             SealRecordMapper sealRecordMapper,
                             ArchiveRecordMapper archiveRecordMapper) {
        this.blockchainRecordMapper = blockchainRecordMapper;
        this.contractMainMapper = contractMainMapper;
        this.sealRecordMapper = sealRecordMapper;
        this.archiveRecordMapper = archiveRecordMapper;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 将合同签章数据锚定到哈希链（默认 ANCHOR 类型）。
     */
    @Transactional
    public BlockchainRecord anchorToChain(Long contractId, Long versionId) {
        return anchorToChain(contractId, versionId, "ANCHOR", null);
    }

    /**
     * 将合同数据锚定到哈希链，指定记录类型和摘要。
     *
     * @param contractId 合同ID
     * @param versionId  版本ID（可为null）
     * @param recordType 记录类型：SEAL / ARCHIVE / ANCHOR
     * @param summary    摘要（可为null，自动生成）
     */
    @Transactional
    public BlockchainRecord anchorToChain(Long contractId, Long versionId, String recordType, String summary) {
        // 获取上一个区块的哈希作为 previousHash
        List<BlockchainRecord> existingRecords = blockchainRecordMapper.selectList(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getContractId, contractId)
                        .orderByDesc(BlockchainRecord::getRecordId));
        String previousHash = existingRecords.isEmpty() ? "0".repeat(64) : existingRecords.get(0).getNodeHash();

        // 生成合同数据快照
        String snapshot = generateSnapshot(contractId);
        log.info("generateSnapshot for contract {}: {} chars", contractId, snapshot.length());

        // 计算当前区块哈希
        String raw = contractId + "|" + (versionId != null ? versionId : "") + "|" + previousHash + "|" + snapshot;
        String nodeHash = sha256Hex(raw);

        // 计算默克尔根（聚合所有该合同的区块哈希）
        List<String> allHashes = existingRecords.stream()
                .map(BlockchainRecord::getNodeHash)
                .collect(Collectors.toList());
        allHashes.add(nodeHash);
        String merkleRoot = computeMerkleRoot(allHashes);

        BlockchainRecord record = new BlockchainRecord();
        record.setContractId(contractId);
        record.setVersionId(versionId);
        record.setRecordType(recordType);
        record.setSummary(summary != null ? summary : ("合同存证锚定 - " + LocalDateTime.now()));
        record.setNodeHash(nodeHash);
        record.setPreviousHash(previousHash);
        record.setMerkleRoot(merkleRoot);
        record.setSnapshotData(snapshot);
        record.setRecordedAt(LocalDateTime.now());
        record.setCreatedBy("BLOCKCHAIN_SVC");
        record.setCreatedAt(LocalDateTime.now());
        blockchainRecordMapper.insert(record);

        // 更新签章记录的区块链哈希
        SealRecord latestSeal = sealRecordMapper.selectOne(
                new LambdaQueryWrapper<SealRecord>()
                        .eq(SealRecord::getContractId, contractId)
                        .orderByDesc(SealRecord::getSealId)
                        .last("LIMIT 1"));
        if (latestSeal != null) {
            latestSeal.setBlockchainHash(nodeHash);
            sealRecordMapper.updateById(latestSeal);
        }

        log.info("区块链锚定成功: contractId={}, type={}, nodeHash={}", contractId, recordType, nodeHash.substring(0, 16));
        return record;
    }

    /**
     * 验证合同哈希链完整性。
     * 重新计算每个区块的哈希并与存储值比对。
     */
    public List<IntegrityResult> verifyIntegrity(Long contractId) {
        List<BlockchainRecord> records = blockchainRecordMapper.selectList(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getContractId, contractId)
                        .orderByAsc(BlockchainRecord::getRecordId));

        List<IntegrityResult> results = new ArrayList<>();
        String chainPrev = "0".repeat(64);

        for (BlockchainRecord record : records) {
            boolean valid;
            String expectedHash;
            try {
                // 用存储的previousHash计算（而非重建链），确保与锚定时完全一致
                String storedPrev = record.getPreviousHash() != null ? record.getPreviousHash() : "0".repeat(64);
                String raw = contractId + "|" + (record.getVersionId() != null ? record.getVersionId() : "")
                        + "|" + storedPrev + "|" + (record.getSnapshotData() != null ? record.getSnapshotData() : "");
                expectedHash = sha256Hex(raw);
                valid = expectedHash.equals(record.getNodeHash());
                // 同时验证哈希链的连续性
                if (valid && !storedPrev.equals(chainPrev)) {
                    valid = false; // 链断裂
                }
                if (valid) {
                    chainPrev = record.getNodeHash();
                }
            } catch (Exception e) {
                expectedHash = "COMPUTE_ERROR";
                valid = false;
            }
            results.add(new IntegrityResult(record.getRecordId(), record.getRecordType(),
                    record.getNodeHash(), expectedHash, valid));
            // chainPrev already updated above — no extra variable needed
        }

        if (results.stream().anyMatch(r -> !r.valid())) {
            log.warn("哈希链完整性验证失败: contractId={}, 失败数量={}",
                    contractId, results.stream().filter(r -> !r.valid()).count());
        }
        return results;
    }

    /**
     * 获取合同的所有区块链记录。
     */
    public List<BlockchainRecord> getRecords(Long contractId) {
        return blockchainRecordMapper.selectList(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getContractId, contractId)
                        .orderByDesc(BlockchainRecord::getRecordId));
    }

    /**
     * 清空所有区块链记录并重新锚定全部合同。
     * 按时间顺序重建哈希链：先签章(SIGNED)记录，再归档(ARCHIVED)记录。
     */
    @Transactional
    public Map<String, Object> backfillAll() {
        // 1. 删除所有现有记录
        List<BlockchainRecord> allOld = blockchainRecordMapper.selectList(null);
        int deleted = allOld.size();
        for (BlockchainRecord r : allOld) {
            blockchainRecordMapper.deleteById(r.getRecordId());
        }
        log.info("已删除 {} 条旧区块链记录", deleted);

        // 2. 收集签章事件和归档事件，按时间排序
        List<ChainEvent> events = new ArrayList<>();

        List<SealRecord> allSeals = sealRecordMapper.selectList(
                new LambdaQueryWrapper<SealRecord>()
                        .orderByAsc(SealRecord::getSealTime));
        for (SealRecord s : allSeals) {
            events.add(new ChainEvent(s.getContractId(), s.getVersionId(), "SEAL",
                    "签章登记锚定", s.getSealTime()));
        }

        List<ArchiveRecord> allArchives = archiveRecordMapper.selectList(
                new LambdaQueryWrapper<ArchiveRecord>()
                        .orderByAsc(ArchiveRecord::getArchiveTime));
        for (ArchiveRecord a : allArchives) {
            events.add(new ChainEvent(a.getContractId(), a.getVersionId(), "ARCHIVE",
                    "归档确认锚定", a.getArchiveTime()));
        }

        events.sort(Comparator.comparing(ChainEvent::eventTime, Comparator.nullsLast(Comparator.naturalOrder())));

        int anchored = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (ChainEvent event : events) {
            try {
                BlockchainRecord rec = anchorToChain(event.contractId, event.versionId,
                        event.recordType, event.summary);
                anchored++;
                details.add(Map.of(
                        "contractId", event.contractId,
                        "recordId", rec.getRecordId(),
                        "type", event.recordType,
                        "nodeHash", rec.getNodeHash().substring(0, 16) + "..."));
            } catch (Exception e) {
                log.error("回填失败: contractId={}, type={}", event.contractId, event.recordType, e);
            }
        }

        log.info("区块链回填完成: 删除{}条, 重新锚定{}条 (签章{}条, 归档{}条)",
                deleted, anchored,
                events.stream().filter(e -> "SEAL".equals(e.recordType)).count(),
                events.stream().filter(e -> "ARCHIVE".equals(e.recordType)).count());
        return Map.of("deleted", deleted, "anchored", anchored, "details", details);
    }

    /**
     * 区块链事件（签章或归档），用于 backfillAll 的时间排序。
     */
    private record ChainEvent(Long contractId, Long versionId, String recordType,
                              String summary, java.time.LocalDateTime eventTime) {}

    /**
     * 生成合同数据 JSON 快照。
     */
    public String generateSnapshot(Long contractId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        ContractMain contract = contractMainMapper.selectById(contractId);
        if (contract != null) {
            snapshot.put("contractId", contract.getContractId());
            snapshot.put("contractNo", contract.getContractNo());
            snapshot.put("title", contract.getTitle());
            snapshot.put("type", contract.getType());
            snapshot.put("amount", contract.getAmount());
            snapshot.put("counterparty", contract.getCounterparty());
            snapshot.put("status", contract.getStatus());
            snapshot.put("signDate", contract.getSignDate());
            snapshot.put("dueDate", contract.getDueDate());
        }
        List<SealRecord> seals = sealRecordMapper.selectList(
                new LambdaQueryWrapper<SealRecord>().eq(SealRecord::getContractId, contractId));
        if (!seals.isEmpty()) {
            List<Map<String, Object>> sealList = new ArrayList<>();
            for (SealRecord s : seals) {
                Map<String, Object> sealMap = new LinkedHashMap<>();
                sealMap.put("sealId", s.getSealId());
                sealMap.put("sealStatus", s.getSealStatus());
                sealMap.put("sealTime", s.getSealTime());
                sealMap.put("signatureProvider", s.getSignatureProvider());
                sealMap.put("fileHash", s.getFileHash());
                sealList.add(sealMap);
            }
            snapshot.put("seals", sealList);
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("生成快照失败: contractId={}", contractId, e);
            return "{}";
        }
    }

    /**
     * 计算默克尔根哈希。
     */
    private String computeMerkleRoot(List<String> hashes) {
        if (hashes.isEmpty()) return "0".repeat(64);
        List<String> current = new ArrayList<>(hashes);
        while (current.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < current.size(); i += 2) {
                String left = current.get(i);
                String right = (i + 1 < current.size()) ? current.get(i + 1) : left;
                next.add(sha256Hex(left + right));
            }
            current = next;
        }
        return current.get(0);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    /**
     * 完整性验证结果。
     */
    public record IntegrityResult(
            Long recordId,
            String recordType,
            String storedHash,
            String computedHash,
            boolean valid
    ) {}
}
