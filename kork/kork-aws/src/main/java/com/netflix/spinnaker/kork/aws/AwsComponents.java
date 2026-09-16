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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

/**
 * AWS SDK v2 client metrics (call latency, retry counts, throttling, etc.) are recorded
 * automatically for every v2 client built anywhere in the JVM via {@link
 * MicrometerExecutionInterceptor}, registered through the SDK's global execution-interceptor
 * classpath discovery mechanism (see {@code
 * software/amazon/awssdk/global/handlers/execution.interceptors} in this module's resources) -- no
 * per-client wiring needed. It resolves the target registry via {@link Metrics#globalRegistry},
 * which {@link #registerWithGlobalRegistry} adds every Spinnaker app's own {@code MeterRegistry}
 * bean to, so metrics land in the same place regardless of whether a caller injects {@code
 * MeterRegistry} directly.
 */
@Configuration
public class AwsComponents {
  @Bean
  @ConditionalOnMissingBean(AwsCredentialsProvider.class)
  AwsCredentialsProvider v2AwsCredentialsProvider() {
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean
  InitializingBean registerWithGlobalRegistry(MeterRegistry meterRegistry) {
    return () -> Metrics.addRegistry(meterRegistry);
  }
}
