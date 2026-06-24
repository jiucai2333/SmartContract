package cupk.smartcontract.dto;

public record ArchiveCreateRequest(
        Long contractId,
        Long versionId
) {}
