/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.config

import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.kork.api.expressions.ExpressionFunctionProvider
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import com.netflix.spinnaker.orca.kayenta.pipeline.functions.KayentaConfigExpressionFunctionProvider
import com.netflix.spinnaker.orca.retrofit.util.RetrofitUtils


@Configuration
@Import(RetrofitConfiguration::class)
@ComponentScan(
  "com.netflix.spinnaker.orca.kayenta.pipeline",
  "com.netflix.spinnaker.orca.kayenta.tasks"
)
@ConditionalOnExpression("\${kayenta.enabled:false}")
class KayentaConfiguration {

  @Bean
  fun kayentaService(
    @Value("\${kayenta.base-url}") kayentaBaseUrl: String,
    serviceClientProvider: ServiceClientProvider,
  ): KayentaService {
    val mapper = OrcaObjectMapper
      .newInstance()
      .disable(WRITE_DATES_AS_TIMESTAMPS) // we want Instant serialized as ISO string
    return serviceClientProvider.getService(
      KayentaService::class.java,
      DefaultServiceEndpoint("kayenta", RetrofitUtils.getBaseUrl(kayentaBaseUrl)),
      mapper)
  }

  @Bean
  fun kayentaExpressionFunctionProvider(kayentaService: KayentaService): ExpressionFunctionProvider {
    return KayentaConfigExpressionFunctionProvider(kayentaService)
  }
}
