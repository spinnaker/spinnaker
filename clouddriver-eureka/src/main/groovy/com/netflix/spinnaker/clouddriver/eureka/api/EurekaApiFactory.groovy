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

import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler
import retrofit.RestAdapter
import retrofit.converter.Converter

class EurekaApiFactory {

  private Converter eurekaConverter
  private OkHttp3ClientConfiguration okHttp3ClientConfiguration

  EurekaApiFactory(Converter eurekaConverter, OkHttp3ClientConfiguration okHttp3ClientConfiguration) {
    this.eurekaConverter = eurekaConverter
    this.okHttp3ClientConfiguration = okHttp3ClientConfiguration
  }

  public EurekaApi createApi(String endpoint) {
    new RestAdapter.Builder()
      .setConverter(eurekaConverter)
      .setClient(new Ok3Client(okHttp3ClientConfiguration.create().build()))
      .setEndpoint(endpoint)
      .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
      .build()
      .create(EurekaApi)
  }
}
