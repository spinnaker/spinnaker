/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */
package com.netflix.spinnaker.front50.config;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig;
import com.netflix.spinnaker.fiat.shared.FiatAccessDeniedExceptionHandler;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.front50.ItemDAOHealthIndicator;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.delivery.DeliveryRepository;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO;
import com.netflix.spinnaker.front50.model.project.ProjectDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.web.context.AuthenticatedRequestContextProvider;
import com.netflix.spinnaker.kork.web.context.RequestContextProvider;
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor;
import java.io.Serializable;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@ComponentScan
@EnableFiatAutoConfig
@EnableScheduling
@EnableConfigurationProperties(StorageServiceConfigurationProperties.class)
public class Front50WebConfig extends WebMvcConfigurerAdapter {

  @Autowired private Registry registry;

  @Bean
  public TaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("scheduler-");
    scheduler.setPoolSize(10);

    return scheduler;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new MetricsInterceptor(
            this.registry,
            "controller.invocations",
            new ArrayList<>(Arrays.asList("account", "application")),
            new ArrayList<>(Collections.singletonList("BasicErrorController"))));
  }

  @Bean
  public FilterRegistrationBean<?> authenticatedRequestFilter() {
    FilterRegistrationBean<?> frb =
        new FilterRegistrationBean<>(new AuthenticatedRequestFilter(true));
    frb.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return frb;
  }

  @Bean
  public ItemDAOHealthIndicator applicationDAOHealthIndicator(
      ApplicationDAO applicationDAO, TaskScheduler taskScheduler) {
    return new ItemDAOHealthIndicator(applicationDAO, taskScheduler);
  }

  @Bean
  public ItemDAOHealthIndicator projectDAOHealthIndicator(
      ProjectDAO projectDAO, TaskScheduler taskScheduler) {
    return new ItemDAOHealthIndicator(projectDAO, taskScheduler);
  }

  @Bean
  public ItemDAOHealthIndicator pipelineDAOHealthIndicator(
      PipelineDAO pipelineDAO, TaskScheduler taskScheduler) {
    return new ItemDAOHealthIndicator(pipelineDAO, taskScheduler);
  }

  @Bean
  @ConditionalOnBean(PipelineTemplateDAO.class)
  public ItemDAOHealthIndicator pipelineTemplateDAOHealthIndicator(
      PipelineTemplateDAO pipelineTemplateDAO, TaskScheduler taskScheduler) {
    return new ItemDAOHealthIndicator(pipelineTemplateDAO, taskScheduler);
  }

  @Bean
  public ItemDAOHealthIndicator pipelineStrategyDAOHealthIndicator(
      PipelineStrategyDAO pipelineStrategyDAO, TaskScheduler taskScheduler) {
    return new ItemDAOHealthIndicator(pipelineStrategyDAO, taskScheduler);
  }

  @Bean
  @ConditionalOnBean(ApplicationPermissionDAO.class)
  public ItemDAOHealthIndicator applicationPermissionDAOHealthIndicator(
      ApplicationPermissionDAO applicationPermissionDAO, TaskScheduler taskScheduler) {
    return new ItemDAOHealthIndicator(applicationPermissionDAO, taskScheduler);
  }

  @Bean
  @ConditionalOnBean(ServiceAccountDAO.class)
  public ItemDAOHealthIndicator serviceAccountDAOHealthIndicator(
      ServiceAccountDAO serviceAccountDAO, TaskScheduler taskScheduler) {
    return new ItemDAOHealthIndicator(serviceAccountDAO, taskScheduler);
  }

  @Bean
  @ConditionalOnBean(DeliveryRepository.class)
  public ItemDAOHealthIndicator deliveryRepositoryHealthIndicator(
      DeliveryRepository deliveryRepository, TaskScheduler taskScheduler) {
    return new ItemDAOHealthIndicator(deliveryRepository, taskScheduler);
  }

  @Bean
  public HystrixRuntimeExceptionHandler hystrixRuntimeExceptionHandler() {
    return new HystrixRuntimeExceptionHandler();
  }

  @Bean
  public FiatAccessDeniedExceptionHandler fiatAccessDeniedExceptionHandler() {
    return new FiatAccessDeniedExceptionHandler();
  }

  @Bean
  public RequestContextProvider requestContextProvider() {
    return new AuthenticatedRequestContextProvider();
  }

  @Bean
  public FiatStatus fiatStatus(
      DynamicConfigService dynamicConfigService,
      Registry registry,
      FiatClientConfigurationProperties fiatClientConfigurationProperties) {
    return new FiatStatus(registry, dynamicConfigService, fiatClientConfigurationProperties);
  }

  @ControllerAdvice
  public static class HystrixRuntimeExceptionHandler {
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ResponseBody
    @ExceptionHandler(HystrixRuntimeException.class)
    public Map<String, Serializable> handleHystrix(HystrixRuntimeException exception) {
      LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(7);
      map.put("fallbackException", exception.getFallbackException().toString());
      map.put("failureType", exception.getFailureType());
      map.put("failureCause", exception.getCause().toString());
      map.put("error", "Hystrix Failure");
      map.put("message", exception.getMessage());
      map.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
      map.put("timestamp", System.currentTimeMillis());
      return map;
    }
  }
}
