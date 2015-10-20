/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.web.DefaultErrorAttributes
import org.springframework.boot.autoconfigure.web.ErrorAttributes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.filter.ShallowEtagHeaderFilter
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

import javax.servlet.Filter

@Configuration
@ComponentScan
public class GateWebConfig extends WebMvcConfigurerAdapter {
  @Autowired
  ExtendedRegistry extendedRegistry

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
      new MetricsInterceptor(
        extendedRegistry, "controller.invocations", ["account", "region", "application"], ["BasicErrorController"]
      )
    )
  }

  @Bean
  Filter eTagFilter() {
    new ShallowEtagHeaderFilter()
  }

  @Bean
  ErrorAttributes errorAttributes() {
    return new ErrorAttributes() {

      DefaultErrorAttributes defaultErrorAttributes = new DefaultErrorAttributes()

      @Override
      Map<String, Object> getErrorAttributes(RequestAttributes attrs, boolean includeStackTrace) {
        Map<String, Object> errorAttributes = defaultErrorAttributes.getErrorAttributes(attrs, includeStackTrace)
        // By default, Spring echoes back the user's requested path. This opens up a potential XSS vulnerability where a
        // user, for example, requests "GET /<script>alert('Hi')</script> HTTP/1.1".
        errorAttributes.remove("path")
        return errorAttributes
      }

      @Override
      Throwable getError(RequestAttributes requestAttributes) {
        return defaultErrorAttributes.getError(requestAttributes)
      }
    }
  }
}
