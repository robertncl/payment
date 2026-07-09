package io.paylab.risk.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RiskConfig {

    /** Injectable clock so velocity-window tests control time instead of sleeping. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
