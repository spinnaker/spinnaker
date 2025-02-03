/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.eureka.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor

class EurekaApiFactory {

  private ServiceClientProvider serviceClientProvider
  private ObjectMapper objectMapper
  private SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor

  EurekaApiFactory(ServiceClientProvider serviceClientProvider, ObjectMapper objectMapper, SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor) {
    this.serviceClientProvider = serviceClientProvider
    this.objectMapper = objectMapper
    this.spinnakerRequestHeaderInterceptor = spinnakerRequestHeaderInterceptor
  }

  public EurekaApi createApi(String endpoint) {
    serviceClientProvider.getService(EurekaApi, new DefaultServiceEndpoint("eurekaapi", endpoint), objectMapper, List.of(spinnakerRequestHeaderInterceptor))
  }
}
