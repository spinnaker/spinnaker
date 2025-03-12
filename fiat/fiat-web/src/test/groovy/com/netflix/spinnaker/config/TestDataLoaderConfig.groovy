/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.config

import com.netflix.spinnaker.fiat.providers.internal.ClouddriverAccountLoader
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverApplicationLoader
import com.netflix.spinnaker.fiat.providers.internal.Front50ApplicationLoader
import com.netflix.spinnaker.fiat.providers.internal.Front50ServiceAccountLoader
import com.netflix.spinnaker.fiat.providers.internal.IgorBuildServiceLoader
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import spock.mock.DetachedMockFactory

@Configuration
@ConditionalOnProperty(name = "dataloaders.stub.enabled", matchIfMissing = true)
class TestDataLoaderConfig {
  private final DetachedMockFactory detachedMockFactory = new DetachedMockFactory();

  @Bean
  @Primary
  ClouddriverApplicationLoader clouddriverApplicationLoader() {
    return detachedMockFactory.Stub(ClouddriverApplicationLoader)
  }

  @Bean
  @Primary
  ClouddriverAccountLoader clouddriverAccountLoader() {
    return detachedMockFactory.Stub(ClouddriverAccountLoader)
  }

  @Bean
  @Primary
  Front50ServiceAccountLoader front50ServiceAccountLoader() {
    return detachedMockFactory.Stub(Front50ServiceAccountLoader)
  }

  @Bean
  @Primary
  Front50ApplicationLoader front50ApplicationLoader() {
    return detachedMockFactory.Stub(Front50ApplicationLoader)
  }

  @Bean
  @Primary
  IgorBuildServiceLoader igorBuildServiceLoader() {
    return detachedMockFactory.Stub(IgorBuildServiceLoader)
  }
}
