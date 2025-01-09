/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.jira.JiraProperties;
import com.netflix.spinnaker.echo.jira.JiraService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@ConditionalOnProperty("jira.enabled")
@EnableConfigurationProperties(JiraProperties.class)
public class JiraConfig {
  private static Logger LOGGER = LoggerFactory.getLogger(JiraConfig.class);

  @Autowired(required = false)
  private OkHttpClient x509ConfiguredClient;

  @Bean
  JiraService jiraService(
      JiraProperties jiraProperties, OkHttp3ClientConfiguration okHttpClientConfig) {
    OkHttpClient okHttpClient;
    if (x509ConfiguredClient != null) {
      LOGGER.info("Using X509 Cert for Jira Client");
      okHttpClient = x509ConfiguredClient;
    } else {
      String credentials =
          String.format("%s:%s", jiraProperties.getUsername(), jiraProperties.getPassword());
      final String basic =
          String.format("Basic %s", Base64.encodeBase64String(credentials.getBytes()));
      BasicAuthRequestInterceptor interceptor = new BasicAuthRequestInterceptor(basic);
      okHttpClient = okHttpClientConfig.createForRetrofit2().addInterceptor(interceptor).build();
    }

    return new Retrofit.Builder()
        .baseUrl(jiraProperties.getBaseUrl())
        .client(okHttpClient)
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(JiraService.class);
  }

  private static class BasicAuthRequestInterceptor implements Interceptor {

    private final String basic;

    public BasicAuthRequestInterceptor(String basic) {
      this.basic = basic;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request =
          chain
              .request()
              .newBuilder()
              .addHeader("Authorization", basic)
              .addHeader("Accept", "application/json")
              .build();
      return chain.proceed(request);
    }
  }
}
