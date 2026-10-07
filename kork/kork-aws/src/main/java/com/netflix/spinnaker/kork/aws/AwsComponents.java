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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

/**
 * AWS SDK v2 client metrics (call latency, retry counts, throttling, etc.) are recorded
 * automatically for every v2 client built anywhere in the JVM via {@code
 * com.netflix.spectator.aws2.SpectatorExecutionInterceptor}, registered through the SDK's global
 * execution-interceptor classpath discovery mechanism (see {@code
 * software/amazon/awssdk/global/handlers/execution.interceptors} in this module's resources). This
 * is the v2-native equivalent of v1's {@code AwsSdkMetrics.setMetricCollector} global hook -- no
 * per-client wiring needed. It resolves the target registry via {@code Spectator.globalRegistry()},
 * which every Spinnaker app's own {@code Registry} bean is added to (see kork-core's {@code
 * SpectatorConfiguration}), so metrics land in the same place regardless of whether a caller
 * injects {@code Registry} directly.
 */
@Configuration
public class AwsComponents {
  @Bean
  @ConditionalOnMissingBean(AwsCredentialsProvider.class)
  AwsCredentialsProvider v2AwsCredentialsProvider() {
    return DefaultCredentialsProvider.builder().build();
  }
}
