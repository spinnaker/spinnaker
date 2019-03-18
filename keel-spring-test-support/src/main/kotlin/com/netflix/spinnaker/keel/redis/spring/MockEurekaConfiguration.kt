/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.redis.spring

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import com.ninjasquad.springmockk.MockkBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockEurekaConfiguration {
  @MockkBean
  lateinit var eurekaClient: EurekaClient

  @Bean
  fun currentInstance(): InstanceInfo = InstanceInfo.Builder.newBuilder()
    .run {
      setAppName("keel")
      setASGName("keel-local")
      build()
    }
}
