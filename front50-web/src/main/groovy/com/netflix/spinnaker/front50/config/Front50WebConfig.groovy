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

package com.netflix.spinnaker.front50.config

import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.front50.exception.BadRequestException
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.embedded.FilterRegistrationBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

import javax.servlet.http.HttpServletResponse

@Configuration
@ComponentScan
@EnableFiatAutoConfig
@EnableConfigurationProperties(StorageServiceConfigurationProperties)
public class Front50WebConfig extends WebMvcConfigurerAdapter {
  @Autowired
  Registry registry

  @Override
  void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new MetricsInterceptor(
            this.registry, "controller.invocations", ["account", "application"], ["BasicErrorController"]
        )
    )
  }

  @Bean
  FilterRegistrationBean authenticatedRequestFilter() {
    def frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(true))
    frb.order = Ordered.HIGHEST_PRECEDENCE
    return frb
  }

  @Bean
  ItemDAOHealthIndicator applicationDAOHealthIndicator(ApplicationDAO applicationDAO) {
    return new ItemDAOHealthIndicator(itemDAO: applicationDAO)
  }

  @Bean
  ItemDAOHealthIndicator projectDAOHealthIndicator(ProjectDAO projectDAO) {
    return new ItemDAOHealthIndicator(itemDAO: projectDAO)
  }

  @Bean
  ItemDAOHealthIndicator pipelineDAOHealthIndicator(PipelineDAO pipelineDAO) {
    return new ItemDAOHealthIndicator(itemDAO: pipelineDAO)
  }

  @Bean
  @ConditionalOnBean(PipelineTemplateDAO)
  ItemDAOHealthIndicator pipelineTemplateDAOHealthIndicator(PipelineTemplateDAO pipelineTemplateDAO) {
    return new ItemDAOHealthIndicator(itemDAO: pipelineTemplateDAO)
  }

  @Bean
  ItemDAOHealthIndicator pipelineStrategyDAOHealthIndicator(PipelineStrategyDAO pipelineStrategyDAO) {
    return new ItemDAOHealthIndicator(itemDAO: pipelineStrategyDAO)
  }

  @Bean
  @ConditionalOnBean(ApplicationPermissionDAO)
  ItemDAOHealthIndicator applicationPermissionDAOHealthIndicator(ApplicationPermissionDAO applicationPermissionDAO) {
    return new ItemDAOHealthIndicator(itemDAO: applicationPermissionDAO)
  }

  @Bean
  @ConditionalOnBean(ServiceAccountDAO)
  ItemDAOHealthIndicator serviceAccountDAOHealthIndicator(ServiceAccountDAO serviceAccountDAO) {
    return new ItemDAOHealthIndicator(itemDAO: serviceAccountDAO)
  }

  @Bean
  HystrixRuntimeExceptionHandler hystrixRuntimeExceptionHandler() {
    return new HystrixRuntimeExceptionHandler()
  }

  @ControllerAdvice
  static class HystrixRuntimeExceptionHandler {
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ResponseBody
    @ExceptionHandler(HystrixRuntimeException)
    Map handleHystrix(HystrixRuntimeException exception) {
      return [
          fallbackException: exception.fallbackException.toString(),
          failureType      : exception.failureType,
          failureCause     : exception.cause.toString(),
          error            : "Hystrix Failure",
          message          : exception.message,
          status           : HttpStatus.TOO_MANY_REQUESTS.value(),
          timestamp        : System.currentTimeMillis()
      ]
    }
  }

  @ControllerAdvice
  static class BadRequestExceptionHandler {
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    @ExceptionHandler(BadRequestException)
    void handleBadRequest(BadRequestException exception, HttpServletResponse response) {
      response.sendError(HttpStatus.BAD_REQUEST.value(), exception.message)
    }
  }
}
