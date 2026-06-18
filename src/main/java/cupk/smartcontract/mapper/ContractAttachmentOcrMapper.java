package cupk.smartcontract.mapper;

import cupk.smartcontract.entity.ContractAttachmentOcr;
import org.apache.ibatis.annotations.Param;

public interface ContractAttachmentOcrMapper {
    ContractAttachmentOcr selectByAttachmentId(@Param("attachmentId") Long attachmentId);

    int insert(ContractAttachmentOcr ocr);

    int updateByAttachmentId(ContractAttachmentOcr ocr);

    int upsertByAttachmentId(ContractAttachmentOcr ocr);

    int logicDeleteByAttachmentId(@Param("attachmentId") Long attachmentId,
                                  @Param("updatedBy") String updatedBy);
}
