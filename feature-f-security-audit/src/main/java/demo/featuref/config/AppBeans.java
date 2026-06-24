package demo.featuref.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.featuref.util.SensitiveDataMasker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppBeans {

    @Bean
    SensitiveDataMasker sensitiveDataMasker(ObjectMapper objectMapper) {
        return new SensitiveDataMasker(objectMapper);
    }
}
