/*
 * Copyright 2019 Netflix, Inc.
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
 */

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.echo.rest.RestService;
import com.netflix.spinnaker.echo.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/** Rest endpoint configuration */
@Configuration
@ConditionalOnProperty("rest.enabled")
public class RestConfig {

  private static final Logger log = LoggerFactory.getLogger(RestConfig.class);

  interface HeadersFromFile {
    Map<String, String> headers(String path);
  }

  @Bean
  HeadersFromFile headersFromFile() {
    return path -> {
      Map<String, String> headers = new HashMap<>();
      try {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
          String line;
          while ((line = br.readLine()) != null) {
            String[] pair = line.split(":");
            if (pair.length == 2) {
              headers.put(pair[0], pair[1].trim());
            } else {
              log.warn("Could not parse header from file={}", path);
            }
          }
        }
      } catch (Exception e) {
        log.error("Error parsing headers from file={}", path, e);
      }
      return headers;
    };
  }

  @Bean
  RestUrls restServices(
      RestProperties restProperties,
      OkHttpClientProvider okHttpClientProvider,
      OkHttp3ClientConfiguration okHttpClientConfig,
      HeadersFromFile headersFromFile) {

    RestUrls restUrls = new RestUrls();

    for (RestProperties.RestEndpointConfiguration endpoint : restProperties.getEndpoints()) {
      Map<String, String> headers = new HashMap<>();

      if (endpoint.getUsername() != null && endpoint.getPassword() != null) {
        String basicAuthCreds = endpoint.getUsername() + ":" + endpoint.getPassword();
        String auth = "Basic " + Base64.encodeBase64String(basicAuthCreds.getBytes());
        headers.put("Authorization", auth);
      }

      if (endpoint.getHeaders() != null) {
        headers.putAll(endpoint.headers);
      }

      if (endpoint.getHeadersFile() != null) {
        headers.putAll(headersFromFile.headers(endpoint.getHeadersFile()));
      }

      BasicAuthRequestInterceptor interceptor = new BasicAuthRequestInterceptor(headers);
      OkHttpClient okHttpClient;
      if (endpoint.insecure) {
        okHttpClient =
            okHttpClientProvider.getClient(
                new DefaultServiceEndpoint(endpoint.getEventName(), endpoint.getUrl(), false));
        if (!headers.isEmpty()) {
          okHttpClient.interceptors().add(interceptor);
        }
      } else if (!headers.isEmpty()) {
        okHttpClient = okHttpClientConfig.createForRetrofit2().addInterceptor(interceptor).build();
      } else {
        okHttpClient = okHttpClientConfig.createForRetrofit2().build();
      }

      Retrofit.Builder retrofitBuilder =
          new Retrofit.Builder()
              .baseUrl(RetrofitUtils.getBaseUrl(endpoint.getUrl()))
              .client(okHttpClient)
              .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
              .addConverterFactory(JacksonConverterFactory.create());

      RestUrls.Service service =
          RestUrls.Service.builder()
              .client(retrofitBuilder.build().create(RestService.class))
              .config(endpoint)
              .build();

      restUrls.getServices().add(service);
    }

    return restUrls;
  }

  private static class BasicAuthRequestInterceptor implements Interceptor {

    private final Map<String, String> headers;

    public BasicAuthRequestInterceptor(Map<String, String> headers) {
      this.headers = headers;
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
      Request.Builder builder = chain.request().newBuilder();
      headers.forEach(builder::addHeader);

      return chain.proceed(builder.build());
    }
  }
}
