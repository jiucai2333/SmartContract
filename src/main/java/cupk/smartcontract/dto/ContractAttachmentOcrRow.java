package cupk.smartcontract.dto;

import cupk.smartcontract.entity.ContractAttachment;
import cupk.smartcontract.entity.ContractAttachmentOcr;
import lombok.Data;

@Data
public class ContractAttachmentOcrRow {
    private ContractAttachment attachment;
    private ContractAttachmentOcr ocr;
}
