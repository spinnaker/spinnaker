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

import static retrofit.Endpoints.newFixedEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.client.ServiceClientFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit.Endpoint;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

@NonnullByDefault
class RetrofitServiceFactory implements ServiceClientFactory {

  private final RestAdapter.LogLevel retrofitLogLevel;
  private final OkHttpClientProvider clientProvider;
  private final RequestInterceptor spinnakerRequestInterceptor;

  RetrofitServiceFactory(
      RestAdapter.LogLevel retrofitLogLevel,
      OkHttpClientProvider clientProvider,
      RequestInterceptor spinnakerRequestInterceptor) {
    this.retrofitLogLevel = retrofitLogLevel;
    this.clientProvider = clientProvider;
    this.spinnakerRequestInterceptor = spinnakerRequestInterceptor;
  }

  @Override
  public <T> T create(Class<T> type, ServiceEndpoint serviceEndpoint, ObjectMapper objectMapper) {
    Endpoint endpoint = newFixedEndpoint(serviceEndpoint.getBaseUrl());
    OkHttpClient client = getClientWithValidInterceptors(clientProvider.getClient(serviceEndpoint));
    return new RestAdapter.Builder()
        .setRequestInterceptor(spinnakerRequestInterceptor)
        .setConverter(new JacksonConverter(objectMapper))
        .setEndpoint(endpoint)
        .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
        .setClient(new Ok3Client(client))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(type))
        .build()
        .create(type);
  }

  @Override
  public <T> T create(
      Class<T> type,
      ServiceEndpoint serviceEndpoint,
      ObjectMapper objectMapper,
      List<Interceptor> interceptors) {
    throw new IllegalArgumentException(
        String.format(
            "Retrofit1 client doesn't support okhttp3 Interceptors. Failed to build %s ",
            type.getName()));
  }

  /**
   * Creates a new client with the correct interceptors for retrofit1 clients. The
   * RawOkHttpClientConfiguration adds retrofit2 interceptors which are not valid for retrofit1
   * clients.
   *
   * @return a new ok client with the correct interceptors
   */
  private OkHttpClient getClientWithValidInterceptors(OkHttpClient client) {
    OkHttpClient.Builder clientBuilder = client.newBuilder();
    clientBuilder.interceptors().clear();
    for (Interceptor interceptor : client.interceptors()) {
      if (!(interceptor instanceof SpinnakerRequestHeaderInterceptor)
          && !(interceptor instanceof Retrofit2EncodeCorrectionInterceptor)) {
        clientBuilder.addInterceptor(interceptor);
      }
    }
    return clientBuilder.build();
  }
}
