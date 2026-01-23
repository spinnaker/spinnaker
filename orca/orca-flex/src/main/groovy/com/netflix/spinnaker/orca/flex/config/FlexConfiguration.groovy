/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.flex.config

import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import com.netflix.spinnaker.orca.flex.FlexService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(RetrofitConfiguration)
@ConditionalOnProperty(value = "flex.base-url")
@ComponentScan([
  "com.netflix.spinnaker.orca.flex.pipeline",
  "com.netflix.spinnaker.orca.flex.tasks"
])
@CompileStatic
class FlexConfiguration {

  @Bean
  FlexService flexService(@Value('${flex.base-url}') String flexBaseUrl, ServiceClientProvider serviceClientProvider) {
    serviceClientProvider.getService(
        FlexService,
        new DefaultServiceEndpoint("flex", RetrofitUtils.getBaseUrl(flexBaseUrl)))
  }
}
