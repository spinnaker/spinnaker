/// *
// * Copyright 2025 Netflix, Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *   http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
// package com.netflix.spinnaker.config;
//
// import com.netflix.spinnaker.kork.observability.model.PluginConfig;
// import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
// import org.springframework.context.annotation.ComponentScan;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.core.Ordered;
// import org.springframework.core.annotation.Order;
// import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// import
// org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
//
//// To avoid collision with other WebSecurityConfigurerAdapters
// @Order(Ordered.HIGHEST_PRECEDENCE + 27)
// @Configuration
// @EnableWebSecurity
// @ComponentScan({"observability.config.metrics.prometheus"})
// public class PrometheusSecurityConfig extends WebSecurityConfigurerAdapter {
//
//  private final PluginConfig pluginConfig;
//
//  public PrometheusSecurityConfig(PluginConfig pluginConfig) {
//    this.pluginConfig = pluginConfig;
//  }
//
//  @Override
//  protected void configure(HttpSecurity http) throws Exception {
//    if (pluginConfig.getMetrics().getPrometheus().isEnabled()) {
//      http.requestMatcher(EndpointRequest.to("aop-prometheus"))
//          .authorizeRequests((requests) -> requests.anyRequest().permitAll());
//    } else {
//      http.requestMatcher(EndpointRequest.to("aop-prometheus"))
//          .authorizeRequests((requests) -> requests.anyRequest().denyAll());
//    }
//  }
// }
