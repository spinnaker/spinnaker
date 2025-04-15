/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.echo.test.config;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.OkHttpMetricsInterceptorProperties;
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Retrofit2TestConfig {

  @Autowired private ObjectFactory<OkHttpClient.Builder> httpClientBuilderFactory;

  @Bean
  public OkHttpClientConfigurationProperties okHttpClientConfigurationProperties() {
    return new OkHttpClientConfigurationProperties();
  }

  @Bean
  public OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties() {
    return new OkHttpMetricsInterceptorProperties();
  }

  @Bean
  public Registry registry() {
    return new NoopRegistry();
  }

  @Bean
  public OkHttp3MetricsInterceptor okHttp3MetricsInterceptor(
      Registry registry, OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties) {
    return new OkHttp3MetricsInterceptor(() -> registry, okHttpMetricsInterceptorProperties);
  }

  @Bean
  public SpinnakerRequestHeaderInterceptor getSpinnakerRequestHeaderInterceptor() {
    return new SpinnakerRequestHeaderInterceptor(false);
  }

  @Bean
  public Retrofit2EncodeCorrectionInterceptor retrofit2EncodeCorrectionInterceptor() {
    return new Retrofit2EncodeCorrectionInterceptor();
  }

  @Bean
  public OkHttp3ClientConfiguration okHttp3ClientConfiguration(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
      OkHttp3MetricsInterceptor okHttp3MetricsInterceptor,
      HttpLoggingInterceptor.Level retrofit2LogLevel,
      SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor,
      Retrofit2EncodeCorrectionInterceptor retrofit2EncodeCorrectionInterceptor) {
    return new OkHttp3ClientConfiguration(
        okHttpClientConfigurationProperties,
        okHttp3MetricsInterceptor,
        retrofit2LogLevel,
        spinnakerRequestHeaderInterceptor,
        retrofit2EncodeCorrectionInterceptor,
        httpClientBuilderFactory);
  }
}
