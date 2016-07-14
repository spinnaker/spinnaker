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

import com.netflix.spinnaker.clouddriver.consul.api.v1.services.AgentApi
import com.squareup.okhttp.OkHttpClient
import retrofit.RestAdapter
import retrofit.client.OkClient

import java.util.concurrent.TimeUnit

class ConsulAgent {
  public AgentApi api

  ConsulAgent(String agentBaseUrl, Long timeout) {
    OkHttpClient client = new OkHttpClient()
    client.setReadTimeout(timeout, TimeUnit.MILLISECONDS)
    this.api = new RestAdapter.Builder()
      .setEndpoint(agentBaseUrl)
      .setClient(new OkClient(client))
      .setLogLevel(RestAdapter.LogLevel.NONE)
      .build()
      .create(AgentApi)
  }
}
