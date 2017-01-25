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

package com.netflix.spinnaker.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.shared.FiatAuthenticationFilter;
import com.netflix.spinnaker.fiat.shared.FiatService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Slf4j
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Configuration
@EnableConfigurationProperties(FiatClientConfigurationProperties.class)
@ConditionalOnExpression("${services.fiat.autoConfig:true}")
@ComponentScan("com.netflix.spinnaker.fiat.shared")
public class FiatAuthenticationConfig {

  @Autowired(required = false)
  @Setter
  private RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.BASIC;

  @Autowired
  @Setter
  private ObjectMapper objectMapper;

  @Autowired
  @Setter
  private OkClient okClient;

  @Bean
  @ConditionalOnMissingBean(FiatService.class) // Allows for override
  public FiatService fiatService(FiatClientConfigurationProperties fiatConfigurationProperties) {
    // New role providers break deserialization if this is not enabled.
    objectMapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(fiatConfigurationProperties.getBaseUrl()))
        .setClient(okClient)
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(FiatService.class))
        .build()
        .create(FiatService.class);
  }

  @Bean
  FilterRegistrationBean fiatFilterRegistrationBean(FiatAuthenticationFilter filter) {
    val frb = new FilterRegistrationBean(filter);
    frb.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    frb.addUrlPatterns("/*");
    return frb;
  }

  @ConditionalOnExpression("!${services.fiat.enabled:false}")
  @Bean
  AnonymousConfig anonymousConfig() {
    return new AnonymousConfig();
  }

  private class AnonymousConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
      // TODO(ttomsu): Make management endpoints non-sensitive?
      log.debug("Fiat service is disabled. Setting Spring Security to allow all traffic.");
      http.authorizeRequests().anyRequest().permitAll().and().csrf().disable();
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
