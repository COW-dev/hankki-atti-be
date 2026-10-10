package com.hankkiatti.global.config;

import com.hankkiatti.domain.notification.service.NotificationJobProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationJobProperties.class)
public class NotificationConfig {
}
