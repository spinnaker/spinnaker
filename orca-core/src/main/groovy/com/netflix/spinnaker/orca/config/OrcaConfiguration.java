/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.events.ExecutionEvent;
import com.netflix.spinnaker.orca.events.ExecutionListenerAdapter;
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.libdiffs.ComparableLooseVersion;
import com.netflix.spinnaker.orca.libdiffs.DefaultComparableLooseVersion;
import com.netflix.spinnaker.orca.listeners.ExecutionCleanupListener;
import com.netflix.spinnaker.orca.listeners.ExecutionListener;
import com.netflix.spinnaker.orca.listeners.MetricsExecutionListener;
import com.netflix.spinnaker.orca.pipeline.*;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack;
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStack;
import com.netflix.spinnaker.orca.pipeline.util.ContextFunctionConfiguration;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import rx.Scheduler;
import rx.schedulers.Schedulers;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Configuration
@ComponentScan({
  "com.netflix.spinnaker.orca.pipeline",
  "com.netflix.spinnaker.orca.notifications.scheduling",
  "com.netflix.spinnaker.orca.deprecation",
  "com.netflix.spinnaker.orca.pipeline.util",
  "com.netflix.spinnaker.orca.telemetry"
})
@EnableConfigurationProperties
public class OrcaConfiguration {
  @Bean public Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean public Duration minInactivity() {
    return Duration.of(3, MINUTES);
  }

  @Bean(destroyMethod = "") public Scheduler scheduler() {
    return Schedulers.io();
  }

  @Bean public ObjectMapper mapper() {
    return OrcaObjectMapper.newInstance();
  }

  @Bean @ConditionalOnMissingBean(name = "pipelineStack")
  public PipelineStack pipelineStack() {
    return new InMemoryPipelineStack();
  }

  @Bean @Order(Ordered.LOWEST_PRECEDENCE)
  public DefaultExceptionHandler defaultExceptionHandler() {
    return new DefaultExceptionHandler();
  }

  @Bean public ExecutionCleanupListener executionCleanupListener() {
    return new ExecutionCleanupListener();
  }

  @Bean
  public ApplicationListener<ExecutionEvent> executionCleanupListenerAdapter(ExecutionListener executionCleanupListener, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(executionCleanupListener, repository);
  }

  @Bean
  public PipelineStarterListener pipelineStarterListener(ExecutionRepository executionRepository, PipelineStartTracker startTracker, ApplicationContext applicationContext) {
    return new PipelineStarterListener(executionRepository, startTracker, applicationContext);
  }

  @Bean
  public ApplicationListener<ExecutionEvent> pipelineStarterListenerAdapter(PipelineStarterListener pipelineStarterListener, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(pipelineStarterListener, repository);
  }

  @Bean
  @ConditionalOnProperty(value = "jarDiffs.enabled", matchIfMissing = false)
  public ComparableLooseVersion comparableLooseVersion() {
    return new DefaultComparableLooseVersion();
  }

  @Bean
  @ConfigurationProperties("userConfiguredUrlRestrictions")
  public UserConfiguredUrlRestrictions.Builder userConfiguredUrlRestrictionProperties() {
    return new UserConfiguredUrlRestrictions.Builder();
  }

  @Bean
  UserConfiguredUrlRestrictions userConfiguredUrlRestrictions(UserConfiguredUrlRestrictions.Builder userConfiguredUrlRestrictionProperties) {
    return userConfiguredUrlRestrictionProperties.build();
  }

  @Bean
  public ContextFunctionConfiguration contextFunctionConfiguration(UserConfiguredUrlRestrictions userConfiguredUrlRestrictions,
                                                                   @Value("${spelEvaluator:v2}")
                                                                     String spelEvaluator) {
    return new ContextFunctionConfiguration(userConfiguredUrlRestrictions, spelEvaluator);
  }

  @Bean
  public ContextParameterProcessor contextParameterProcessor(ContextFunctionConfiguration contextFunctionConfiguration) {
    return new ContextParameterProcessor(contextFunctionConfiguration);
  }

  @Bean
  public ApplicationListener<ExecutionEvent> onCompleteMetricExecutionListenerAdapter(Registry registry, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(new MetricsExecutionListener(registry), repository);
  }

  @Bean
  @ConditionalOnMissingBean(StageDefinitionBuilderFactory.class)
  public StageDefinitionBuilderFactory stageDefinitionBuilderFactory(Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    return new DefaultStageDefinitionBuilderFactory(stageDefinitionBuilders);
  }

  @Bean
  public RetrySupport retrySupport() {
    return new RetrySupport();
  }

}
