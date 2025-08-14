/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.retrofit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.client.ServiceClientFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@NonnullByDefault
public class Retrofit2ServiceFactory implements ServiceClientFactory {

  private final OkHttpClientProvider clientProvider;

  public Retrofit2ServiceFactory(OkHttpClientProvider clientProvider) {
    this.clientProvider = clientProvider;
  }

  @Override
  public <T> T create(Class<T> type, ServiceEndpoint serviceEndpoint, ObjectMapper objectMapper) {
    return create(type, serviceEndpoint, objectMapper, List.of());
  }

  @Override
  public <T> T create(
      Class<T> type,
      ServiceEndpoint serviceEndpoint,
      ObjectMapper objectMapper,
      List<Interceptor> interceptors) {
    OkHttpClient okHttpClient = clientProvider.getClient(serviceEndpoint, interceptors);

    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(serviceEndpoint.getBaseUrl()))
        .client(okHttpClient)
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .build()
        .create(type);
  }
}
