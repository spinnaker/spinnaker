/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.web.config

import groovy.util.logging.Slf4j

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowire
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@ComponentScan(['com.netflix.spinnaker.orca.controllers', 'com.netflix.spinnaker.orca.util'])
@CompileStatic
@EnableFiatAutoConfig
@Slf4j
class WebConfiguration {

  @Bean
  MetricsInterceptor metricsInterceptor(Registry registry) {
    return  new MetricsInterceptor(registry, "controller.invocations", ["application"], ["BasicErrorController"])
  }

  @Bean
  WebMvcConfigurer webMvcConfigurer(MetricsInterceptor metricsInterceptor) {
    return new WebMvcConfigurer() {
      @Override
      void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(metricsInterceptor)
      }
    }
  }

  @Bean(name = "objectMapper", autowire = Autowire.BY_TYPE) ObjectMapper orcaObjectMapper() {
    OrcaObjectMapper.getInstance()
  }

  @Bean
  FilterRegistrationBean authenticatedRequestFilter() {
    def frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(true))
    frb.order = Ordered.HIGHEST_PRECEDENCE
    return frb
  }

  @Bean
  Filter simpleCORSFilter() {
    new Filter() {
      public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type");
        chain.doFilter(req, res);
      }

      public void init(FilterConfig filterConfig) {}

      public void destroy() {}
    }
  }
}
