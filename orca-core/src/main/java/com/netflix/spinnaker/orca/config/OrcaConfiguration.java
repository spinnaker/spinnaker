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

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.context.annotation.AnnotationConfigUtils.EVENT_LISTENER_FACTORY_BEAN_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.PluginsAutoConfiguration;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.orca.StageResolver;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResolver;
import com.netflix.spinnaker.orca.api.SimpleStage;
import com.netflix.spinnaker.orca.commands.ForceExecutionCancellationCommand;
import com.netflix.spinnaker.orca.events.ExecutionEvent;
import com.netflix.spinnaker.orca.events.ExecutionListenerAdapter;
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.libdiffs.ComparableLooseVersion;
import com.netflix.spinnaker.orca.libdiffs.DefaultComparableLooseVersion;
import com.netflix.spinnaker.orca.listeners.*;
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import rx.Scheduler;
import rx.schedulers.Schedulers;

@Configuration
@ComponentScan({
  "com.netflix.spinnaker.orca.capabilities",
  "com.netflix.spinnaker.orca.pipeline",
  "com.netflix.spinnaker.orca.deprecation",
  "com.netflix.spinnaker.orca.pipeline.util",
  "com.netflix.spinnaker.orca.preprocessors",
  "com.netflix.spinnaker.orca.telemetry",
  "com.netflix.spinnaker.orca.notifications.scheduling",
})
@Import({
  PreprocessorConfiguration.class,
  PluginsAutoConfiguration.class,
})
@EnableConfigurationProperties
public class OrcaConfiguration {
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  public Duration minInactivity() {
    return Duration.of(3, MINUTES);
  }

  @Bean(destroyMethod = "")
  public Scheduler scheduler() {
    return Schedulers.io();
  }

  @Bean
  public ObjectMapper mapper() {
    return OrcaObjectMapper.getInstance();
  }

  @Bean
  @Order
  public DefaultExceptionHandler defaultExceptionHandler() {
    return new DefaultExceptionHandler();
  }

  @Bean
  public ExecutionCleanupListener executionCleanupListener() {
    return new ExecutionCleanupListener();
  }

  @Bean
  public ApplicationListener<ExecutionEvent> executionCleanupListenerAdapter(
      ExecutionListener executionCleanupListener, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(executionCleanupListener, repository);
  }

  @Bean
  @ConditionalOnProperty(value = "jar-diffs.enabled", matchIfMissing = false)
  public ComparableLooseVersion comparableLooseVersion() {
    return new DefaultComparableLooseVersion();
  }

  @Bean
  @ConfigurationProperties("user-configured-url-restrictions")
  public UserConfiguredUrlRestrictions.Builder userConfiguredUrlRestrictionProperties() {
    return new UserConfiguredUrlRestrictions.Builder();
  }

  @Bean
  UserConfiguredUrlRestrictions userConfiguredUrlRestrictions(
      UserConfiguredUrlRestrictions.Builder userConfiguredUrlRestrictionProperties) {
    return userConfiguredUrlRestrictionProperties.build();
  }

  @Bean
  public ContextParameterProcessor contextParameterProcessor(
      List<ExpressionFunctionProvider> expressionFunctionProviders,
      PluginManager pluginManager,
      DynamicConfigService dynamicConfigService) {
    return new ContextParameterProcessor(
        expressionFunctionProviders, pluginManager, dynamicConfigService);
  }

  @Bean
  public ApplicationListener<ExecutionEvent> onCompleteMetricExecutionListenerAdapter(
      Registry registry, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(new MetricsExecutionListener(registry), repository);
  }

  @Bean
  @ConditionalOnMissingBean(StageDefinitionBuilderFactory.class)
  public StageDefinitionBuilderFactory stageDefinitionBuilderFactory(StageResolver stageResolver) {
    return new DefaultStageDefinitionBuilderFactory(stageResolver);
  }

  @Bean
  public RetrySupport retrySupport() {
    return new RetrySupport();
  }

  @Bean
  public ApplicationEventMulticaster applicationEventMulticaster(
      @Qualifier("applicationEventTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
    // TODO rz - Add error handlers
    SimpleApplicationEventMulticaster async = new SimpleApplicationEventMulticaster();
    async.setTaskExecutor(taskExecutor);
    SimpleApplicationEventMulticaster sync = new SimpleApplicationEventMulticaster();

    return new DelegatingApplicationEventMulticaster(sync, async);
  }

  @Bean
  public ThreadPoolTaskExecutor applicationEventTaskExecutor() {
    ThreadPoolTaskExecutor threadPool = new ThreadPoolTaskExecutor();
    threadPool.setThreadNamePrefix("events-");
    threadPool.setCorePoolSize(20);
    threadPool.setMaxPoolSize(20);
    return threadPool;
  }

  @Bean
  public ThreadPoolTaskExecutor cancellableStageExecutor() {
    ThreadPoolTaskExecutor threadPool = new ThreadPoolTaskExecutor();
    threadPool.setThreadNamePrefix("cancel-");
    threadPool.setCorePoolSize(5);
    threadPool.setMaxPoolSize(10);
    threadPool.setQueueCapacity(20);
    return threadPool;
  }

  @Bean
  public TaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("scheduler-");
    scheduler.setPoolSize(10);
    return scheduler;
  }

  @Bean
  public TaskResolver taskResolver(Collection<Task> tasks) {
    return new TaskResolver(tasks, true);
  }

  @Bean
  public StageResolver stageResolver(
      Collection<StageDefinitionBuilder> stageDefinitionBuilders,
      Optional<List<SimpleStage>> simpleStages) {
    List<SimpleStage> stages = simpleStages.orElseGet(ArrayList::new);
    return new StageResolver(stageDefinitionBuilders, stages);
  }

  @Bean(name = EVENT_LISTENER_FACTORY_BEAN_NAME)
  public EventListenerFactory eventListenerFactory() {
    return new InspectableEventListenerFactory();
  }

  @Bean
  public ForceExecutionCancellationCommand forceExecutionCancellationCommand(
      ExecutionRepository executionRepository, Clock clock) {
    return new ForceExecutionCancellationCommand(executionRepository, clock);
  }
}
