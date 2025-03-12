/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kork.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.metrics.AwsSdkMetrics;
import com.amazonaws.retry.RetryPolicy;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.aws.SpectatorMetricCollector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsComponents {
  @Bean
  @ConditionalOnMissingBean(AWSCredentialsProvider.class)
  AWSCredentialsProvider awsCredentialsProvider() {
    return new DefaultAWSCredentialsProviderChain();
  }

  @Bean
  RetryPolicy.RetryCondition instrumentedRetryCondition(Registry registry) {
    return new InstrumentedRetryCondition(registry);
  }

  @Bean
  RetryPolicy.BackoffStrategy instrumentedBackoffStrategy(Registry registry) {
    return new InstrumentedBackoffStrategy(registry);
  }

  @Bean
  @ConditionalOnProperty(value = "aws.metrics.enabled", matchIfMissing = true)
  SpectatorMetricCollector spectatorMetricsCollector(Registry registry) {
    SpectatorMetricCollector collector = new SpectatorMetricCollector(registry);
    AwsSdkMetrics.setMetricCollector(collector);
    return collector;
  }
}
