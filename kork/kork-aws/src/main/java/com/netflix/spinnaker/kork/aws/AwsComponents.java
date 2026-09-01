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

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.aws2.SpectatorExecutionInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

@Configuration
public class AwsComponents {
  @Bean
  @ConditionalOnMissingBean(AwsCredentialsProvider.class)
  AwsCredentialsProvider v2AwsCredentialsProvider() {
    return DefaultCredentialsProvider.builder().build();
  }

  /**
   * A v2 {@code ExecutionInterceptor} that records AWS SDK v2 client metrics (call latency, retry
   * counts, throttling, etc.) to the Spectator {@link Registry}. Attach it to a client via {@code
   * ClientOverrideConfiguration.builder().addExecutionInterceptor(...)}; it isn't applied
   * automatically, since v2 has no global metrics hook equivalent to v1's {@code
   * AwsSdkMetrics.setMetricCollector}.
   */
  @Bean
  @ConditionalOnMissingBean(SpectatorExecutionInterceptor.class)
  SpectatorExecutionInterceptor spectatorExecutionInterceptor(Registry registry) {
    return new SpectatorExecutionInterceptor(registry);
  }
}
