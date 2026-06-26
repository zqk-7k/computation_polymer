package com.vaspshow.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DatasetProperties.class, AuthProperties.class, AssistantProperties.class, IntakeProperties.class})
public class BackendConfig {
}
