/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.config

import com.netflix.spinnaker.clouddriver.scattergather.ScatterGather
import com.netflix.spinnaker.clouddriver.scattergather.client.ScatteredOkHttpCallFactory
import com.netflix.spinnaker.clouddriver.scattergather.naive.NaiveScatterGather
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class ScatterGatherConfiguration {

  @Bean
  open fun scatteredOkHttpCallFactory(okHttp3ClientConfiguration: OkHttp3ClientConfiguration): ScatteredOkHttpCallFactory {
    return ScatteredOkHttpCallFactory(okHttp3ClientConfiguration.create().build())
  }

  @Bean
  open fun scatterGather(callFactory: ScatteredOkHttpCallFactory): ScatterGather {
    return NaiveScatterGather(callFactory)
  }
}
