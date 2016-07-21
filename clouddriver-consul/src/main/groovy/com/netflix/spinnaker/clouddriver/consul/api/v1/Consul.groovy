/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.consul.api.v1

import com.netflix.spinnaker.clouddriver.consul.api.v1.services.ConsulApi
import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.consul.config.ConsulProperties
import com.squareup.okhttp.OkHttpClient
import retrofit.RestAdapter
import retrofit.client.OkClient

class Consul<T extends ConsulApi> {
  T api
  String endpoint
  Long timeout

  Consul(ConsulConfig config) {
    if (!config.enabled) {
      throw new IllegalArgumentException("Consul not enabled, cannot create Consul API")
    }
    Consul(config.agentEndpoint, ConsulProperties.DEFAULT_TIMEOUT_MILLIS)
  }

  Consul(String endpoint, Long timeout) {
    this.endpoint = endpoint
    this.timeout = timeout
    this.api = new RestAdapter.Builder()
      .setEndpoint(endpoint)
      .setClient(new OkClient(new OkHttpClient()))
      .setLogLevel(RestAdapter.LogLevel.NONE)
      .build()
      .create(T)
  }
}
