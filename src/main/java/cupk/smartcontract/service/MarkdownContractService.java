package cupk.smartcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cupk.smartcontract.dto.MarkdownImportVO;
import cupk.smartcontract.dto.MarkdownImportVO.ParsedClause;
import cupk.smartcontract.dto.MarkdownImportVO.ParsedRisk;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.entity.ContractVersion;
import cupk.smartcontract.entity.FulfillmentPlan;
import cupk.smartcontract.entity.RiskItem;
import cupk.smartcontract.mapper.ContractMainMapper;
import cupk.smartcontract.mapper.ContractVersionMapper;
import cupk.smartcontract.mapper.FulfillmentPlanMapper;
import cupk.smartcontract.mapper.RiskItemMapper;
import cupk.smartcontract.security.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 合同导入导出服务。
 * 支持合同导出为结构化 Markdown，也支持从 Markdown 解析元数据、条款、风险和履约计划。
 */
@Service
public class MarkdownContractService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    private static final Pattern FM_LINE_PATTERN = Pattern.compile("^(\\w[\\w_]*)\\s*:\\s*(.*)\\s*$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern INLINE_RISK_PATTERN = Pattern.compile("<!--\\s*risk\\s*:\\s*(HIGH|MEDIUM|LOW|CRITICAL)\\s+(.*?)-->", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?\\s*[-:]{3,}\\s*\\|", Pattern.MULTILINE);

    private final ContractMainMapper contractMapper;
    private final RiskItemMapper riskMapper;
    private final FulfillmentPlanMapper planMapper;
    private final ContractVersionMapper versionMapper;

    public MarkdownContractService(ContractMainMapper contractMapper,
                                   RiskItemMapper riskMapper,
                                   FulfillmentPlanMapper planMapper,
                                   ContractVersionMapper versionMapper) {
        this.contractMapper = contractMapper;
        this.riskMapper = riskMapper;
        this.planMapper = planMapper;
        this.versionMapper = versionMapper;
    }

    public record MarkdownExport(String filename, String markdown) {}

    public MarkdownExport exportMarkdown(Long contractId) {
        ContractMain contract = contractMapper.selectById(contractId);
        if (contract == null) return null;

        List<RiskItem> risks = riskMapper.selectList(new LambdaQueryWrapper<RiskItem>()
                .eq(RiskItem::getContractId, contractId)
                .orderByDesc(RiskItem::getCreatedAt));
        List<FulfillmentPlan> plans = planMapper.selectList(new LambdaQueryWrapper<FulfillmentPlan>()
                .eq(FulfillmentPlan::getContractId, contractId)
                .orderByAsc(FulfillmentPlan::getDueDate));
        List<ContractVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<ContractVersion>()
                .eq(ContractVersion::getContractId, contractId)
                .orderByDesc(ContractVersion::getCreatedAt));

        String content = versions.isEmpty() || !StringUtils.hasText(versions.get(0).getContent())
                ? defaultContractBody(contract)
                : versions.get(0).getContent();

        StringBuilder md = new StringBuilder(4096);
        appendFrontMatter(md, contract);
        md.append("# ").append(StringUtils.hasText(contract.getTitle()) ? contract.getTitle() : "未命名合同").append("\n\n");
        md.append("> **AI 合规声明**：本文件由智能合同管理系统辅助生成或整理，请在提交审批、签章和归档前完成法务复核。\n\n");
        md.append(content).append("\n\n");
        appendRisks(md, risks);
        appendFulfillmentPlans(md, plans);
        md.append("---\n\n");
        md.append("> 本文件由智能合同管理系统生成 | 导出时间：")
                .append(LocalDateTime.now().format(DATETIME_FMT))
                .append(" | 合同编号：").append(nvl(contract.getContractNo())).append("\n");
        return new MarkdownExport(buildFilename(contract) + ".md", md.toString());
    }

    public MarkdownImportVO importFromMarkdown(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalArgumentException("Markdown 内容不能为空");
        }
        String frontMatterRaw = null;
        String body = markdown;
        Matcher fmMatcher = FRONT_MATTER_PATTERN.matcher(markdown);
        if (fmMatcher.find()) {
            frontMatterRaw = fmMatcher.group(1);
            body = markdown.substring(fmMatcher.end());
        }
        Map<String, String> meta = parseFrontMatter(frontMatterRaw);
        List<ParsedClause> clauses = parseClauses(body);
        List<ParsedRisk> risks = parseRisks(body);
        List<Map<String, String>> deliverables = parseTable(body, "交付|deliverable");
        List<Map<String, String>> paymentSchedule = parseTable(body, "付款|payment|阶段");

        return new MarkdownImportVO(
                meta.get("contract_no"),
                meta.get("title"),
                meta.get("type"),
                meta.get("party_a"),
                meta.get("party_b"),
                parseAmount(meta.get("amount")),
                parseDate(meta.get("sign_date")),
                parseDate(meta.get("due_date")),
                meta.get("risk_level"),
                meta.getOrDefault("status", "DRAFT"),
                body,
                clauses,
                risks,
                deliverables,
                paymentSchedule,
                clauses.size(),
                risks.size(),
                deliverables.size(),
                paymentSchedule.size()
        );
    }

    public ContractMain importAndCreateContract(String markdown, String username) {
        MarkdownImportVO result = importFromMarkdown(markdown);
        ContractMain contract = new ContractMain();
        contract.setContractNo(StringUtils.hasText(result.contractNo()) ? result.contractNo()
                : "HT-" + LocalDate.now().getYear() + "-" + (System.currentTimeMillis() % 100000));
        contract.setTitle(StringUtils.hasText(result.title()) ? result.title() : "Markdown导入合同");
        contract.setType(StringUtils.hasText(result.type()) ? result.type() : "OTHER");
        contract.setAmount(result.amount() == null ? BigDecimal.ZERO : result.amount());
        contract.setCounterparty(StringUtils.hasText(result.partyB()) ? result.partyB() : "未填写相对方");
        contract.setDeptId(SecurityContext.deptId() != null ? SecurityContext.deptId() : 1L);
        contract.setOwnerId(SecurityContext.userId() != null ? SecurityContext.userId() : 1L);
        contract.setStatus(StringUtils.hasText(result.status()) ? result.status() : "DRAFT");
        contract.setRiskLevel(StringUtils.hasText(result.riskLevel())
                ? normalizeRiskLevel(result.riskLevel())
                : autoRiskLevel(contract.getAmount()));
        contract.setSignDate(result.signDate());
        contract.setDueDate(result.dueDate());
        contract.setCreatedBy(username);
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        contract.setDeleted(0);
        contractMapper.insert(contract);

        for (ParsedRisk r : result.risks()) {
            RiskItem risk = new RiskItem();
            risk.setContractId(contract.getContractId());
            risk.setVersionId(0L);
            risk.setRiskLevel(normalizeRiskLevel(r.riskLevel()));
            risk.setClauseRef(r.clauseRef());
            risk.setSuggestion(r.suggestion());
            risk.setRiskType("LEGAL");
            risk.setReviewStatus("PENDING");
            risk.setCreatedAt(LocalDateTime.now());
            riskMapper.insert(risk);
        }
        for (Map<String, String> d : result.deliverables()) {
            FulfillmentPlan plan = new FulfillmentPlan();
            plan.setContractId(contract.getContractId());
            plan.setDueDate(contract.getDueDate() != null ? contract.getDueDate() : LocalDate.now().plusDays(30));
            plan.setOwnerId(contract.getOwnerId());
            plan.setMilestoneName(d.getOrDefault("交付物", d.getOrDefault("name", "未命名节点")));
            plan.setStatus("PENDING");
            plan.setCreatedBy(username);
            plan.setCreatedAt(LocalDateTime.now());
            planMapper.insert(plan);
        }
        return contract;
    }

    private void appendFrontMatter(StringBuilder md, ContractMain c) {
        md.append("---\n");
        md.append("contract_no: ").append(nvl(c.getContractNo())).append("\n");
        md.append("title: ").append(nvl(c.getTitle())).append("\n");
        md.append("type: ").append(nvl(c.getType())).append("\n");
        md.append("party_b: ").append(nvl(c.getCounterparty())).append("\n");
        md.append("amount: ").append(c.getAmount() == null ? "" : c.getAmount()).append("\n");
        md.append("due_date: ").append(c.getDueDate() == null ? "" : c.getDueDate().format(DATE_FMT)).append("\n");
        md.append("risk_level: ").append(nvl(c.getRiskLevel())).append("\n");
        md.append("status: ").append(nvl(c.getStatus())).append("\n");
        md.append("---\n\n");
    }

    private String defaultContractBody(ContractMain c) {
        StringBuilder md = new StringBuilder();
        md.append("## 第一条 项目概述\n\n");
        md.append("本合同标的为：").append(nvl(c.getTitle())).append("。\n\n");
        md.append("## 第二条 费用与支付\n\n");
        md.append("| 阶段 | 比例 | 金额 | 触发条件 |\n");
        md.append("| --- | --- | --- | --- |\n");
        md.append("| 签约款 | 30% | ").append(formatPercent(c.getAmount(), 30)).append(" | 合同签订后 7 个工作日 |\n");
        md.append("| 中期款 | 30% | ").append(formatPercent(c.getAmount(), 30)).append(" | 中期交付物验收通过 |\n");
        md.append("| 验收款 | 40% | ").append(formatPercent(c.getAmount(), 40)).append(" | 终验合格 |\n\n");
        md.append("## 第三条 知识产权\n\n");
        md.append("（待补充知识产权条款）\n\n");
        md.append("## 第四条 违约责任\n\n");
        md.append("（待补充违约责任条款）\n");
        return md.toString();
    }

    private void appendRisks(StringBuilder md, List<RiskItem> risks) {
        if (risks == null || risks.isEmpty()) return;
        md.append("\n## 审核报告摘要\n\n");
        md.append("| 序号 | 风险等级 | 关联条款 | 风险类型 | 建议措施 |\n");
        md.append("| --- | --- | --- | --- | --- |\n");
        int i = 1;
        for (RiskItem risk : risks) {
            md.append("| ").append(i++).append(" | ")
                    .append(nvl(risk.getRiskLevel())).append(" | ")
                    .append(nvl(risk.getClauseRef())).append(" | ")
                    .append(nvl(risk.getRiskType())).append(" | ")
                    .append(nvl(risk.getSuggestion()).replace("\n", " ")).append(" |\n");
        }
        md.append("\n");
    }

    private void appendFulfillmentPlans(StringBuilder md, List<FulfillmentPlan> plans) {
        if (plans == null || plans.isEmpty()) return;
        md.append("\n## 履约与交付计划\n\n");
        md.append("| 序号 | 交付物 | 截止日期 | 状态 |\n");
        md.append("| --- | --- | --- | --- |\n");
        int i = 1;
        for (FulfillmentPlan plan : plans) {
            md.append("| ").append(i++).append(" | ")
                    .append(nvl(plan.getMilestoneName())).append(" | ")
                    .append(plan.getDueDate() == null ? "待定" : plan.getDueDate().format(DATE_FMT)).append(" | ")
                    .append(nvl(plan.getStatus())).append(" |\n");
        }
        md.append("\n");
    }

    private Map<String, String> parseFrontMatter(String raw) {
        Map<String, String> meta = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) return meta;
        for (String line : raw.split("\\R")) {
            Matcher matcher = FM_LINE_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                meta.put(matcher.group(1), stripQuotes(matcher.group(2)));
            }
        }
        return meta;
    }

    private List<ParsedClause> parseClauses(String body) {
        List<ParsedClause> clauses = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(body);
        List<int[]> headings = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new int[]{matcher.start(), matcher.end()});
            titles.add(matcher.group(2).trim());
        }
        for (int i = 0; i < headings.size(); i++) {
            int start = headings.get(i)[1];
            int end = i + 1 < headings.size() ? headings.get(i + 1)[0] : body.length();
            String content = body.substring(start, end).trim();
            clauses.add(new ParsedClause(i + 1, titles.get(i), content, extractInlineRisk(content)));
        }
        return clauses;
    }

    private List<ParsedRisk> parseRisks(String body) {
        List<ParsedRisk> risks = new ArrayList<>();
        Matcher matcher = INLINE_RISK_PATTERN.matcher(body);
        while (matcher.find()) {
            String level = normalizeRiskLevel(matcher.group(1));
            String suggestion = matcher.group(2).trim();
            risks.add(new ParsedRisk(level, nearestHeading(body, matcher.start()), suggestion, suggestion));
        }
        return risks;
    }

    private String normalizeRiskLevel(String level) {
        if (!StringUtils.hasText(level)) return "LOW";
        String normalized = level.trim().toUpperCase();
        return "CRITICAL".equals(normalized) ? "HIGH" : normalized;
    }

    private List<Map<String, String>> parseTable(String body, String headingKeywordRegex) {
        List<Map<String, String>> rows = new ArrayList<>();
        String[] lines = body.split("\\R");
        for (int i = 0; i < lines.length - 2; i++) {
            if (!lines[i].contains("|") || !TABLE_SEPARATOR.matcher(lines[i + 1]).find()) continue;
            String header = lines[i];
            if (!Pattern.compile(headingKeywordRegex, Pattern.CASE_INSENSITIVE).matcher(header).find()) continue;
            List<String> headers = splitTableLine(header);
            int j = i + 2;
            while (j < lines.length && lines[j].contains("|")) {
                List<String> values = splitTableLine(lines[j]);
                Map<String, String> row = new LinkedHashMap<>();
                for (int k = 0; k < Math.min(headers.size(), values.size()); k++) {
                    row.put(headers.get(k), values.get(k));
                }
                rows.add(row);
                j++;
            }
        }
        return rows;
    }

    private List<String> splitTableLine(String line) {
        String clean = line.trim();
        if (clean.startsWith("|")) clean = clean.substring(1);
        if (clean.endsWith("|")) clean = clean.substring(0, clean.length() - 1);
        List<String> result = new ArrayList<>();
        for (String cell : clean.split("\\|")) {
            result.add(cell.trim());
        }
        return result;
    }

    private String nearestHeading(String body, int position) {
        Matcher matcher = HEADING_PATTERN.matcher(body.substring(0, Math.min(position, body.length())));
        String nearest = "未知条款";
        while (matcher.find()) {
            nearest = matcher.group(2).trim();
        }
        return nearest;
    }

    private String extractInlineRisk(String content) {
        Matcher matcher = INLINE_RISK_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1).toUpperCase() + ": " + matcher.group(2).trim() : null;
    }

    private BigDecimal parseAmount(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value.replace(",", "").replace("¥", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return LocalDate.parse(value.trim(), DATE_FMT);
        } catch (Exception ex) {
            return null;
        }
    }

    private String stripQuotes(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String formatPercent(BigDecimal total, int percent) {
        if (total == null) return "待定";
        return total.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100)).toPlainString();
    }

    private String autoRiskLevel(BigDecimal amount) {
        if (amount == null) return "LOW";
        if (amount.compareTo(BigDecimal.valueOf(500000)) >= 0) return "HIGH";
        if (amount.compareTo(BigDecimal.valueOf(100000)) >= 0) return "MEDIUM";
        return "LOW";
    }

    private String buildFilename(ContractMain contract) {
        String title = StringUtils.hasText(contract.getTitle()) ? contract.getTitle() : "合同";
        String no = StringUtils.hasText(contract.getContractNo()) ? contract.getContractNo() : "未编号";
        return (no + "_" + title).replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
