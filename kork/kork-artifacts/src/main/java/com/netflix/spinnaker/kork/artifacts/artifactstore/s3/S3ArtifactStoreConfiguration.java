/*
 * Copyright 2023 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.artifacts.artifactstore.s3;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfigurationProperties;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreGetter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreStorer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.artifactstore.entities.EntityStoreConfiguration;
import com.netflix.spinnaker.security.UserPermissionEvaluator;
import java.net.URI;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@Import(EntityStoreConfiguration.class)
@Log4j2
@ConditionalOnProperty(name = "artifact-store.type", havingValue = "s3")
public class S3ArtifactStoreConfiguration {

  @Bean
  @ConditionalOnExpression("${artifact-store.s3.enabled:false}")
  public ArtifactStoreStorer artifactStoreStorer(
      ArtifactStoreConfigurationProperties properties,
      @Qualifier("artifactS3Client") S3Client s3Client,
      ArtifactStoreURIBuilder artifactStoreURIBuilder) {
    return new S3ArtifactStoreStorer(
        s3Client,
        properties.getS3().getBucket(),
        artifactStoreURIBuilder,
        properties.getApplicationsRegex());
  }

  @Bean
  public ArtifactStoreGetter artifactStoreGetter(
      Optional<UserPermissionEvaluator> userPermissionEvaluator,
      ArtifactStoreConfigurationProperties properties,
      @Qualifier("artifactS3Client") S3Client s3Client) {

    if (userPermissionEvaluator.isEmpty()) {
      log.warn(
          "UserPermissionEvaluator is not present. This means anyone will be able to access any artifact in the store.");
    }

    String bucket = properties.getS3().getBucket();

    return new S3ArtifactStoreGetter(s3Client, userPermissionEvaluator.orElse(null), bucket);
  }

  @Bean
  public S3Client artifactS3Client(
      ArtifactStoreConfigurationProperties properties,
      @Nullable AwsCredentialsProvider awsCredentialsProvider) {
    ArtifactStoreConfigurationProperties.S3ClientConfig config = properties.getS3();

    // If we do not have a magic bean AwsCredentialsProvider, use the built-in logic
    if (awsCredentialsProvider == null) {
      awsCredentialsProvider = getCredentialsProvider(config);
    }

    S3ClientBuilder builder =
        S3Client.builder()
            .credentialsProvider(awsCredentialsProvider)
            .forcePathStyle(config.isForcePathStyle());

    // Overwriting the URL is primarily used for S3 compatible object stores
    // like seaweedfs
    if (config.getUrl() != null) {
      builder = builder.endpointOverride(URI.create(config.getUrl()));
    }

    if (config.getRegion() != null) {
      builder = builder.region(Region.of(config.getRegion()));
    }

    return builder.build();
  }

  private AwsCredentialsProvider getCredentialsProvider(
      ArtifactStoreConfigurationProperties.S3ClientConfig config) {
    if (config.getAccessKey() != null) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
    } else if (config.getProfile() != null) {
      return ProfileCredentialsProvider.create(config.getProfile());
    } else if (config.getUrl() != null) {
      return AnonymousCredentialsProvider.create();
    }

    return DefaultCredentialsProvider.create();
  }
}
