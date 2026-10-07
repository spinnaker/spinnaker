/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.configserver.autoconfig;

import io.awspring.cloud.s3.S3ProtocolResolver;
import java.net.URI;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * The Spring Cloud Config resources API uses a Spring ResourceLoader to provide access to files in
 * AWS S3 using an "s3://" resource scheme. This configuration registers that ResourceLoader support
 * (via {@link S3ProtocolResolver}) only when Spring Cloud Config is actually configured with an S3
 * remote repository, matching the "awss3" profile Spring Cloud Config itself uses to activate
 * {@code AwsS3EnvironmentRepository}. Deployments that don't use S3 for halconfig never build an
 * {@link S3Client} or need a resolvable AWS region.
 */
@Configuration
@Profile("awss3")
@Conditional(RemoteConfigSourceConfigured.class)
public class AwsS3ProtocolResolverConfiguration {

  // Static factory method, per Spring's guidance for beans that are themselves
  // BeanFactoryPostProcessor/BeanPostProcessor implementations (S3ProtocolResolver is both a
  // BeanFactoryPostProcessor and ResourceLoaderAware): this lets Spring instantiate it early,
  // before the owning @Configuration class would otherwise need to be fully initialized.
  @Bean
  static S3ProtocolResolver s3ProtocolResolver() {
    return new S3ProtocolResolver();
  }

  @Bean
  S3Client configServerS3Client(AwsS3EnvironmentProperties s3EnvironmentProperties) {
    S3ClientBuilder builder = S3Client.builder();
    if (StringUtils.isNotBlank(s3EnvironmentProperties.getRegion())) {
      builder.region(Region.of(s3EnvironmentProperties.getRegion()));
    }
    if (StringUtils.isNotBlank(s3EnvironmentProperties.getEndpoint())) {
      builder.endpointOverride(URI.create(s3EnvironmentProperties.getEndpoint()));
    }
    return builder.build();
  }
}
