package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.front50.plugins.PluginBinaryStorageService;
import com.netflix.spinnaker.front50.plugins.S3PluginBinaryStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 configuration for Front50.
 *
 * <p>Only S3 plugin-binary storage ({@code spinnaker.s3.plugin-storage}) is supported. Front50
 * metadata (applications, pipelines, ...) can no longer be stored in S3; use SQL.
 */
@Configuration
@ConditionalOnProperty("spinnaker.s3.enabled")
@EnableConfigurationProperties(S3PluginStorageProperties.class)
public class S3Config {

  @Bean
  @ConditionalOnProperty("spinnaker.s3.plugin-storage.enabled")
  public S3Client awsS3PluginClient(
      AwsCredentialsProvider awsCredentialsProvider, S3PluginStorageProperties s3Properties) {
    return S3ClientFactory.create(awsCredentialsProvider, s3Properties);
  }

  @Bean
  @ConditionalOnProperty("spinnaker.s3.plugin-storage.enabled")
  PluginBinaryStorageService pluginBinaryStorageService(
      S3Client awsS3PluginClient, S3PluginStorageProperties properties) {
    return new S3PluginBinaryStorageService(awsS3PluginClient, properties);
  }
}
