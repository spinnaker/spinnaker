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

package com.netflix.spinnaker.fiat.shared;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.OkHttpClientConfiguration;
import com.netflix.spinnaker.okhttp.OkHttpMetricsInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.squareup.okhttp.OkHttpClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Slf4j
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Configuration
@EnableConfigurationProperties(FiatClientConfigurationProperties.class)
@ComponentScan("com.netflix.spinnaker.fiat.shared")
public class FiatAuthenticationConfig {

  @Autowired(required = false)
  @Setter
  private RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.BASIC;

  @Bean
  @ConditionalOnMissingBean(FiatService.class) // Allows for override
  public FiatService fiatService(Registry registry,
                                 FiatClientConfigurationProperties fiatConfigurationProperties,
                                 SpinnakerRequestInterceptor interceptor,
                                 OkHttpClientConfiguration okHttpClientConfiguration) {
    // New role providers break deserialization if this is not enabled.
    val objectMapper = new ObjectMapper();
    objectMapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    OkHttpClient okHttpClient = okHttpClientConfiguration.create();

    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(fiatConfigurationProperties.getBaseUrl()))
        .setRequestInterceptor(interceptor)
        .setClient(new OkClient(okHttpClient))
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(FiatService.class))
        .build()
        .create(FiatService.class);
  }

  @Bean
  FiatWebSecurityConfigurerAdapter fiatSecurityConfig(FiatStatus fiatStatus) {
    return new FiatWebSecurityConfigurerAdapter(fiatStatus);
  }

  private class FiatWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
    private final FiatStatus fiatStatus;

    private FiatWebSecurityConfigurerAdapter(FiatStatus fiatStatus) {
      this.fiatStatus = fiatStatus;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        /*
         * Having `FiatAuthenticationFilter` prior to `SecurityContextPersistenceFilter` results in the
         * `SecurityContextHolder` being overridden with a null value.
         *
         * The null value then causes the `AnonymousAuthenticationFilter` to inject an "anonymousUser" which when
         * passed over the wire to fiat is promptly rejected.
         *
         * This behavior is triggered when `management.security.enabled` is `false`.
         */
        http
            .csrf().disable()
            .addFilterAfter(new FiatAuthenticationFilter(fiatStatus), SecurityContextPersistenceFilter.class);
    }
  }

  private static class Slf4jRetrofitLogger implements RestAdapter.Log {
    private final Logger logger;

    Slf4jRetrofitLogger(Class type) {
      this(LoggerFactory.getLogger(type));
    }

    Slf4jRetrofitLogger(Logger logger) {
      this.logger = logger;
    }

    @Override
    public void log(String message) {
      logger.info(message);
    }
  }
}
