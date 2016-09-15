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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.shared.FiatAuthenticationFilter;
import com.netflix.spinnaker.fiat.shared.FiatService;
import lombok.Setter;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
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

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Configuration
@ComponentScan("com.netflix.spinnaker.fiat.shared")
public class FiatAuthenticationConfig {

  @Autowired(required = false)
  @Setter
  private RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.NONE;

  @Autowired
  @Setter
  private ObjectMapper objectMapper;

  @Autowired
  @Setter
  private OkClient okClient;

  @Value("${services.fiat.baseUrl}")
  @Setter
  private String fiatEndpoint;

  @Bean
  public FiatService fiatService() {
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(fiatEndpoint))
        .setClient(okClient)
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(retrofitLogLevel)
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
      http.authorizeRequests().anyRequest().permitAll().and().csrf().disable();
    }
  }
}
