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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.gate.services.PagerDutyService
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import groovy.transform.CompileStatic
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@CompileStatic
@ConditionalOnProperty('pager-duty.token')
class PagerDutyConfig {

  @Value('${pagerDuty.token}')
  String pagerDutyToken

  @Value('${pager-duty.base-url}')
  String pagerBaseUrl


  @Bean
  PagerDutyService pagerDutyService(ServiceClientProvider serviceClientProvider) {
    List<Interceptor> interceptors = [new RequestHeaderInterceptor(pagerDutyToken)] as List<Interceptor>

    return serviceClientProvider.getService(
      PagerDutyService,
      new DefaultServiceEndpoint("pagerduty", pagerBaseUrl),
      new ObjectMapper(),
      interceptors)
  }

  static class RequestHeaderInterceptor implements Interceptor {

    private final String token;

    RequestHeaderInterceptor(String token ){
      this.token = token
    }

    @Override
    Response intercept(Chain chain) throws IOException {
      Request request = chain.request();

      Request newRequest = request.newBuilder()
        .header("Authorization", "Token token=${token}")
        .header("Accept", "application/vnd.pagerduty+json;version=2")
        .build();

      return chain.proceed(newRequest);
    }
  }
}
