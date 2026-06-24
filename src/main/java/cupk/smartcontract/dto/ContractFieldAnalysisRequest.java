package cupk.smartcontract.dto;

public record ContractFieldAnalysisRequest(
        Long contractId,
        Long versionId,
        String html,
        String plainText,
        String contractType
) {
}

