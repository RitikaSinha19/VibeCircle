package com.vibecircle.call;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CallProperties.class)
public class CallConfig {
}
