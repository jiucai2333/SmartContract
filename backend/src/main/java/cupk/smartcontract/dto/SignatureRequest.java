package cupk.smartcontract.dto;

/**
 * 电子签章请求 DTO。
 * 由前端 signature.html 提交，经 SignatureController 转发至 SignatureService。
 */
public record SignatureRequest(
        Long contractId,
        Long versionId,
        Long fileId,
        String signerName,
        String signerMobile,
        SignPosition position,
        String signType
) {
    /**
     * 签章在文件中的位置信息。
     */
    public record SignPosition(
            int x,
            int y,
            int pageNum
    ) {}
}
