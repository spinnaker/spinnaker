/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Component
public class ServiceInterfaceFactory {
  @Autowired OkHttp3ClientConfiguration okHttp3ClientConfiguration;

  public <T> T createService(String endpoint, SpinnakerService<T> service) {
    Class<T> clazz = service.getEndpointClass();
    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(endpoint))
        .client(okHttp3ClientConfiguration.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(clazz);
  }
}
