/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config

import com.netflix.spinnaker.gate.services.PagerDutyService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@CompileStatic
@ConditionalOnProperty('pager-duty.token')
class PagerDutyConfig {

  @Value('${pagerDuty.token}')
  String token

  @Bean
  Endpoint pagerDutyEndpoint(
    @Value('${pager-duty.base-url}') String pagerBaseUrl) {
    newFixedEndpoint(pagerBaseUrl)
  }

  RequestInterceptor requestInterceptor = new RequestInterceptor() {
    @Override
    void intercept(RequestInterceptor.RequestFacade request) {
      request.addHeader("Authorization", "Token token=${token}")
      request.addHeader("Accept", "application/vnd.pagerduty+json;version=2")
    }
  }

  @Bean
  PagerDutyService pagerDutyService(Endpoint pagerDutyEndpoint) {
    new RestAdapter.Builder()
      .setEndpoint(pagerDutyEndpoint)
      .setClient(new OkClient())
      .setConverter(new JacksonConverter())
      .setRequestInterceptor(requestInterceptor)
      .build()
      .create(PagerDutyService)
  }
}

