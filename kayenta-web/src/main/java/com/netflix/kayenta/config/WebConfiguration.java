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
 */

package com.netflix.kayenta.config;

import com.google.common.collect.ImmutableList;
import com.netflix.kayenta.filters.KayentaCorsFilter;
import com.netflix.kayenta.interceptors.MetricsInterceptor;
import com.netflix.spectator.api.Registry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@Slf4j
@ComponentScan({"com.netflix.kayenta.controllers"})
public class WebConfiguration extends WebMvcConfigurerAdapter {

  @Autowired
  Registry registry;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
      new MetricsInterceptor(
        this.registry,
        "controller.invocations",
        ImmutableList.of("accountName", "configurationAccountName", "metricsAccountName", "storageAccountName", "application"),
        ImmutableList.of("BasicErrorController")
      )
    );
  }

  @Bean
  FilterRegistrationBean simpleCORSFilter() {
    FilterRegistrationBean frb = new FilterRegistrationBean(new KayentaCorsFilter());
    frb.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return frb;
  }
}
