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

package com.netflix.spinnaker.orca.kato.tasks.quip

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.squareup.okhttp.OkHttpClient
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import static retrofit.RestAdapter.LogLevel.BASIC

@Deprecated
abstract class AbstractQuipTask implements Task {
  InstanceService createInstanceService(String address) {
    RestAdapter restAdapter = new RestAdapter.Builder()
      .setEndpoint(address)
      .setConverter(new JacksonConverter())
      .setClient(new OkClient(new OkHttpClient(retryOnConnectionFailure: false)))
      .setLogLevel(BASIC)
      .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
      .build()
    return restAdapter.create(InstanceService.class)
  }
}
