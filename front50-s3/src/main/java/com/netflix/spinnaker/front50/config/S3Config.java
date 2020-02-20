package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.kork.aws.bastion.BastionConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty("spinnaker.s3.enabled")
@Import({
  BastionConfig.class,
  S3StorageServiceConfiguration.class,
  S3PluginStorageConfiguration.class
})
@EnableConfigurationProperties({S3MetadataStorageProperties.class, S3PluginStorageProperties.class})
public class S3Config extends CommonStorageServiceDAOConfig {}
