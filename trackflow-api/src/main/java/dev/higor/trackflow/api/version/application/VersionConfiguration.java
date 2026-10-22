package dev.higor.trackflow.api.version.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VersionProperties.class)
class VersionConfiguration {}
