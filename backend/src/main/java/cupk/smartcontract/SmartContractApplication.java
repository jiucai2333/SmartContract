package cupk.smartcontract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("cupk.smartcontract.mapper")
public class SmartContractApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartContractApplication.class, args);
    }

}
