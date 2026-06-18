package cupk.smartcontract.dto;

import java.util.List;

/**
 * 合同导入/OCR 结果 VO。
 * 仅包含 OCR 识别状态、版式分析标识、HTML 内容和纯文本，
 * 不携带附件元数据（附件元数据由 AttachmentVO 提供）。
 *
 * <p>使用场景：{@code GET /api/attachments/{id}/ocr}、{@code POST /api/attachments/{id}/ocr}</p>
 */
public record ContractImportResultVO(
        /** 关联的附件 ID */
        Long attachmentId,
        /** 附件文件名（最小必要信息，方便前端展示） */
        String fileName,
        /** OCR 状态：PROCESSING / SUCCESS / FAILED / PENDING */
        String ocrStatus,
        /** OCR 失败时的错误信息 */
        String ocrError,
        /** 识别到的页数 */
        Integer pageCount,
        /** 是否已保存 PaddleOCR 原始 JSON */
        boolean ocrRawJsonExist,
        /** 是否已保存结构化 OCR blocks JSON */
        boolean ocrBlocksJsonExist,
        /** 是否已保存 Qwen 版式分析 JSON */
        boolean qwenLayoutJsonExist,
        /** OCR 引擎型号 */
        String ocrModel,
        /** Qwen 版式分析型号 */
        String qwenModel,
        /** OCR 耗时（毫秒） */
        Long ocrDurationMs,
        /** Qwen 版式分析耗时（毫秒） */
        Long qwenDurationMs,
        /** OCR 结果是否近似（基于坐标估算） */
        Boolean approximate,
        /** 坐标版式预览 HTML（用于只读 OCR 还原效果展示） */
        String previewHtml,
        /** 语义编辑器 HTML（用于 contenteditable 编辑器） */
        String editorHtml,
        /** OCR 识别纯文本全文 */
        String plainText,
        /** 纯文本预览（最多 200 字符） */
        String plainTextPreview,
        /** 处理过程中的告警列表 */
        List<String> warnings,
        /** 解析来源标识：paddleocr / qwen_layout / plain_text / legacy_markdown / DOCX_POI 等 */
        String parseSource
) {
}
