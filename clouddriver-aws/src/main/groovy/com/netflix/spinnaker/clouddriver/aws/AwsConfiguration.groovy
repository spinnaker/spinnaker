/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws

import com.amazonaws.metrics.AwsSdkMetrics
import com.amazonaws.retry.RetryPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.spectator.aws.SpectatorMetricsCollector
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentialsInitializer
import com.netflix.spinnaker.kork.aws.AwsComponents
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

import javax.annotation.PostConstruct

@Configuration
@ConditionalOnProperty('aws.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.aws"])
@Import([
  BastionConfig,
  AmazonCredentialsInitializer,
  AwsComponents
])
class AwsConfiguration {
  @Value('${aws.metrics.enabled:false}')
  boolean metricsEnabled

  @Autowired
  SpectatorMetricsCollector spectatorMetricsCollector

  @PostConstruct
  void checkMetricsEnabled() {
    if (!metricsEnabled) {
      AwsSdkMetrics.setMetricCollector(null)
    }
  }
  @Bean
  AmazonClientProvider amazonClientProvider(RetryPolicy.RetryCondition instrumentedRetryCondition, RetryPolicy.BackoffStrategy instrumentedBackoffStrategy) {
    new AmazonClientProvider.Builder()
      .backoffStrategy(instrumentedBackoffStrategy)
      .retryCondition(instrumentedRetryCondition)
      .objectMapper(amazonObjectMapper())
      .build()
  }

  @Bean
  ObjectMapper amazonObjectMapper() {
    new AmazonObjectMapper()
  }
}
