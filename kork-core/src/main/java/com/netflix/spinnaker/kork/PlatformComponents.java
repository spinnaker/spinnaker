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

package com.netflix.spinnaker.kork;

import com.netflix.spinnaker.kork.dynamicconfig.TransientConfigConfiguration;
import com.netflix.spinnaker.kork.metrics.SpectatorConfiguration;
import com.netflix.spinnaker.kork.version.ServiceVersion;
import com.netflix.spinnaker.kork.version.SpringPackageVersionResolver;
import com.netflix.spinnaker.kork.version.VersionResolver;
import io.github.resilience4j.circuitbreaker.autoconfigure.CircuitBreakersHealthIndicatorAutoConfiguration;
import io.github.resilience4j.ratelimiter.autoconfigure.RateLimitersHealthIndicatorAutoConfiguration;
import java.util.List;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
  TransientConfigConfiguration.class,
  SpectatorConfiguration.class,
})
@ImportAutoConfiguration(
    exclude = {
      CircuitBreakersHealthIndicatorAutoConfiguration.class,
      RateLimitersHealthIndicatorAutoConfiguration.class
    })
public class PlatformComponents {

  @Bean
  @ConditionalOnMissingBean(ServiceVersion.class)
  ServiceVersion serviceVersion(
      ApplicationContext applicationContext, List<VersionResolver> versionResolvers) {
    return new ServiceVersion(applicationContext, versionResolvers);
  }

  @Bean
  @ConditionalOnMissingBean(SpringPackageVersionResolver.class)
  VersionResolver springPackageVersionResolver(ApplicationContext applicationContext) {
    return new SpringPackageVersionResolver(applicationContext);
  }
}
