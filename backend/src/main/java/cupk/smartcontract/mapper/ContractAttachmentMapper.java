package cupk.smartcontract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cupk.smartcontract.dto.ContractAttachmentOcrRow;
import cupk.smartcontract.entity.ContractAttachment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ContractAttachmentMapper extends BaseMapper<ContractAttachment> {
    List<ContractAttachmentOcrRow> selectWithOcr(@Param("contractId") Long contractId,
                                                 @Param("ocrStatus") String ocrStatus);
}
