package cupk.smartcontract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cupk.smartcontract.entity.ContractMain;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ContractMainMapper extends BaseMapper<ContractMain> {

    @Select("SELECT contract_no FROM contract_main WHERE contract_no LIKE CONCAT(#{prefix}, '%')")
    List<String> selectContractNumbersByPrefix(@Param("prefix") String prefix);

    @Select("SELECT contract_id FROM contract_main WHERE contract_id = #{contractId} FOR UPDATE")
    Long lockById(@Param("contractId") Long contractId);
}
