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

package com.netflix.spinnaker.rosco

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

@Configuration
@CompileStatic
public class WebConfig extends WebMvcConfigurerAdapter {

  @Autowired
  Registry registry

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
      new MetricsInterceptor(
        this.registry, "controller.invocations", ["cloudProvider", "region"], ["BasicErrorController"]
      )
    )
  }

  @Bean
  FilterRegistrationBean authenticatedRequestFilter() {
    def frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(true))
    frb.order = Ordered.HIGHEST_PRECEDENCE
    return frb
  }

}
