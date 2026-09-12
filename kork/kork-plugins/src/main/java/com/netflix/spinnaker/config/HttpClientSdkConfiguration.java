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
 */
package com.netflix.spinnaker.config;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static tools.jackson.databind.cfg.DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS;
import static tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory;
import com.netflix.spinnaker.kork.plugins.sdk.httpclient.HttpClientSdkFactory;
import com.netflix.spinnaker.kork.plugins.sdk.httpclient.OkHttp3ClientFactory;
import com.netflix.spinnaker.kork.plugins.sdk.httpclient.internal.CompositeOkHttpClientFactory;
import com.netflix.spinnaker.kork.plugins.sdk.httpclient.internal.DefaultOkHttp3ClientFactory;
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.retrofit.Retrofit2ConfigurationProperties;
import jakarta.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.kotlin.KotlinModule;

@Configuration
public class HttpClientSdkConfiguration {

  @Bean
  public static SdkFactory httpClientSdkFactory(
      List<OkHttp3ClientFactory> okHttpClientFactories,
      Environment environment,
      Provider<Registry> registry) {

    OkHttpClientConfigurationProperties okHttpClientProperties =
        Binder.get(environment)
            .bind("ok-http-client", Bindable.of(OkHttpClientConfigurationProperties.class))
            .orElse(new OkHttpClientConfigurationProperties());

    OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties =
        Binder.get(environment)
            .bind(
                "ok-http-client.interceptor", Bindable.of(OkHttpMetricsInterceptorProperties.class))
            .orElse(new OkHttpMetricsInterceptorProperties());

    Retrofit2ConfigurationProperties retrofit2ConfigurationProperties =
        Binder.get(environment)
            .bind("retrofit2", Bindable.of(Retrofit2ConfigurationProperties.class))
            .orElse(new Retrofit2ConfigurationProperties());

    List<OkHttp3ClientFactory> factories = new ArrayList<>(okHttpClientFactories);
    OkHttp3MetricsInterceptor okHttp3MetricsInterceptor =
        new OkHttp3MetricsInterceptor(registry, okHttpMetricsInterceptorProperties);
    factories.add(
        new DefaultOkHttp3ClientFactory(
            okHttp3MetricsInterceptor, retrofit2ConfigurationProperties.getLogLevel()));

    OkHttp3ClientConfiguration config =
        new OkHttp3ClientConfiguration(
            okHttpClientProperties,
            okHttp3MetricsInterceptor,
            retrofit2ConfigurationProperties.getLogLevel());

    KotlinModule kotlinModule = new KotlinModule.Builder().build();

    // TODO(rz): It'd be nice to make this customizable, but I'm not sure how to do that without
    //  bringing Jackson into the Plugin SDK (quite undesirable).
    ObjectMapper objectMapper =
        JsonMapper.builder()
            .addModule(kotlinModule)
            .disable(READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .serializationInclusion(NON_NULL)
            .build();

    return new HttpClientSdkFactory(
        new CompositeOkHttpClientFactory(factories), environment, objectMapper, config);
  }
}
