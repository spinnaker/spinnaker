/*
 * Copyright 2026 Apple Inc.
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

import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Disables awspring's per-service auto-configurations (S3, SNS, SQS, DynamoDB, Secrets Manager,
 * Parameter Store, SES, CloudWatch metrics export) by default. {@code spring-cloud-aws-starter-s3}
 * is on the classpath so Spring's {@code ResourceLoader} can resolve {@code s3://} halconfig
 * locations, but every one of these auto-configurations eagerly instantiates its AWS SDK v2 client
 * as a singleton bean at context startup -- e.g. {@code S3AutoConfiguration} additionally registers
 * a global {@code ProtocolResolver} that builds an S3 client for every resource lookup, not just
 * {@code s3://} ones. None of these Spring-managed clients are actually used by Spinnaker code, and
 * requiring a resolvable AWS region just to start up breaks any environment (local dev, CI, tests)
 * that doesn't have one configured. Deployments that do want one of these can re-enable it by
 * overriding {@code spring.autoconfigure.exclude} without this class's name.
 */
public class AwsS3ProtocolResolverDefaultsEnvironmentPostProcessor
    implements EnvironmentPostProcessor {

  private static final List<String> AWSPRING_CLIENT_AUTOCONFIGURATIONS =
      List.of(
          "io.awspring.cloud.autoconfigure.metrics.CloudWatchExportAutoConfiguration",
          "io.awspring.cloud.autoconfigure.ses.SesAutoConfiguration",
          "io.awspring.cloud.autoconfigure.s3.S3TransferManagerAutoConfiguration",
          "io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration",
          "io.awspring.cloud.autoconfigure.s3.S3CrtAsyncClientAutoConfiguration",
          "io.awspring.cloud.autoconfigure.sns.SnsAutoConfiguration",
          "io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration",
          "io.awspring.cloud.autoconfigure.dynamodb.DynamoDbAutoConfiguration",
          "io.awspring.cloud.autoconfigure.config.secretsmanager.SecretsManagerReloadAutoConfiguration",
          "io.awspring.cloud.autoconfigure.config.secretsmanager.SecretsManagerAutoConfiguration",
          "io.awspring.cloud.autoconfigure.config.parameterstore.ParameterStoreReloadAutoConfiguration",
          "io.awspring.cloud.autoconfigure.config.parameterstore.ParameterStoreAutoConfiguration",
          "io.awspring.cloud.autoconfigure.config.s3.S3ReloadAutoConfiguration");

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                "kork-cloud-config-server-defaults",
                Map.of(
                    "spring.autoconfigure.exclude",
                    String.join(",", AWSPRING_CLIENT_AUTOCONFIGURATIONS))));
  }
}
