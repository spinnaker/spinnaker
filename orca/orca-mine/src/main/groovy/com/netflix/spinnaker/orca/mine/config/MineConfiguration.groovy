/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.mine.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import com.netflix.spinnaker.orca.mine.MineService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan([
  "com.netflix.spinnaker.orca.mine.pipeline",
  "com.netflix.spinnaker.orca.mine.tasks"
])
@ConditionalOnProperty(value = "mine.base-url")
class MineConfiguration {

  @Autowired
  ObjectMapper objectMapper

  @Bean
  MineService mineService(@Value('${mine.base-url}') String mineBaseUrl, ServiceClientProvider serviceClientProvider) {
    serviceClientProvider.getService(
        MineService,
        new DefaultServiceEndpoint("mine", RetrofitUtils.getBaseUrl(mineBaseUrl)))
  }
}
