package com.keyloop.challenge.infrastructure.config;

import com.keyloop.challenge.domain.service.ResourceAssignmentPolicy;
import com.keyloop.challenge.domain.service.SkillFitPolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {
    @Bean
    ResourceAssignmentPolicy resourceAssignmentPolicy() {
        return new SkillFitPolicy();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
