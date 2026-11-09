package com.careerradar.resume;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ResumeStorageProperties.class)
public class ResumeConfig {
}
