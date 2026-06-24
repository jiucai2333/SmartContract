package cupk.smartcontract.controller;

import cupk.smartcontract.common.Result;
import cupk.smartcontract.dto.MarkdownImportVO;
import cupk.smartcontract.entity.ContractMain;
import cupk.smartcontract.security.RequireRole;
import cupk.smartcontract.security.SecurityContext;
import cupk.smartcontract.service.MarkdownContractService;
import cupk.smartcontract.service.MarkdownContractService.MarkdownExport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Markdown 导入导出 Controller。
 * 支持将合同导出为 Markdown 文件，也支持从 Markdown 解析或创建合同。
 */
@RestController
@RequestMapping("/api")
public class MarkdownController {

    private final MarkdownContractService markdownService;

    public MarkdownController(MarkdownContractService markdownService) {
        this.markdownService = markdownService;
    }

    /**
     * 导出合同为 Markdown 文件。
     */
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @GetMapping("/contracts/{contractId}/export/markdown")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable Long contractId) {
        MarkdownExport export = markdownService.exportMarkdown(contractId);
        if (export == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] bytes = export.markdown().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(export.filename(), StandardCharsets.UTF_8))
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(bytes);
    }

    /**
     * 预览 Markdown 导出内容，不触发下载。
     */
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @GetMapping("/contracts/{contractId}/export/markdown/preview")
    public ResponseEntity<Map<String, String>> previewMarkdown(@PathVariable Long contractId) {
        MarkdownExport export = markdownService.exportMarkdown(contractId);
        if (export == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "filename", export.filename(),
                "markdown", export.markdown()
        ));
    }

    /**
     * 解析上传的 Markdown 内容，返回结构化预览结果。
     */
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @PostMapping("/contracts/import/markdown/parse")
    public Result parseMarkdown(@RequestBody Map<String, String> body) {
        String markdown = body.get("markdown");
        if (markdown == null || markdown.isBlank()) {
            return Result.error("Markdown 内容不能为空");
        }

        MarkdownImportVO result = markdownService.importFromMarkdown(markdown);
        return Result.success(result);
    }

    /**
     * 根据 Markdown 内容创建合同，并返回创建结果和解析结果。
     */
    @RequireRole({"USER", "DEPT_LEADER", "LEGAL", "FINANCE", "EXECUTIVE", "ADMIN"})
    @PostMapping("/contracts/import/markdown")
    public Result importAndCreate(@RequestBody Map<String, String> body) {
        String markdown = body.get("markdown");
        if (markdown == null || markdown.isBlank()) {
            return Result.error("Markdown 内容不能为空");
        }

        String username = SecurityContext.username();
        if (username == null) {
            username = "system";
        }

        try {
            ContractMain contract = markdownService.importAndCreateContract(markdown, username);
            MarkdownImportVO parsed = markdownService.importFromMarkdown(markdown);
            return Result.success(Map.of(
                    "contract", contract,
                    "parsed", parsed
            ));
        } catch (Exception e) {
            return Result.error("导入失败：" + e.getMessage());
        }
    }

    /**
     * 获取 Markdown 格式说明。
     */
    @GetMapping("/contracts/import/markdown/spec")
    public Map<String, Object> markdownSpec() {
        return Map.of(
                "formatVersion", "1.0.0",
                "description", "智能合同管理系统 Markdown 导入导出格式说明",
                "frontMatter", Map.of(
                        "required", List.of("title", "type", "amount"),
                        "optional", List.of("contract_no", "party_a", "party_b", "due_date", "risk_level", "status", "sign_date"),
                        "example", "---\ncontract_no: CON-2026-0001\ntitle: 技术服务合同\ntype: TECH\namount: 500000.00\n---"
                ),
                "riskAnnotations", Map.of(
                        "inline", "<!--risk:LEVEL suggestion--> 条款正文",
                        "blockquote", "> ⚠️ **风险提示**：请补充付款条件",
                        "levels", List.of("HIGH", "MEDIUM", "LOW"),
                        "criticalMapping", "CRITICAL is normalized to HIGH"
                ),
                "tables", Map.of(
                        "deliverables", "| 序号 | 交付物 | 验收标准 | 截止日期 |",
                        "paymentSchedule", "| 阶段 | 比例 | 金额 | 触发条件 |"
                ),
                "clauseStructure", "## 第X条 条款标题 + ### X.Y 子条款",
                "aiCompliance", "AI 生成或 OCR 导入内容应由人工复核后使用"
        );
    }
}
