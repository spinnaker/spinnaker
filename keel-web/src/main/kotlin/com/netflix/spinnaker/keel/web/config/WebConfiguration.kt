/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.web.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

@Configuration
@ComponentScan(basePackages = ["com.netflix.spinnaker.keel.controllers.v1"])
open class WebConfiguration
@Autowired constructor(
  private val registry: Registry
) : WebMvcConfigurerAdapter() {

  override fun addInterceptors(registry: InterceptorRegistry) {
    registry.addInterceptor(MetricsInterceptor(
      this.registry, "controller.invocations", listOf("application"), listOf("BasicErrorController")
    ))
  }

  @Bean open fun authenticatedRequestFilter()
    = FilterRegistrationBean(AuthenticatedRequestFilter(true))
        .apply { order = Ordered.HIGHEST_PRECEDENCE }

  @Bean open fun simpleCORSFilter()
    = object : Filter {
        override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
          if (response is HttpServletResponse) {
            response.setHeader("Access-Control-Allow-Origin", "*")
            response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE")
            response.setHeader("Access-Control-Max-Age", "3600")
            response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type")
          }
          chain.doFilter(request, response)
        }

        override fun init(filterConfig: FilterConfig?) {}
        override fun destroy() {}
      }
}
