/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.config;

import static java.lang.String.format;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.data.task.DualTaskRepository;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty("dual-task-repository.enabled")
@EnableConfigurationProperties(DualTaskRepositoryConfiguration.Properties.class)
public class DualTaskRepositoryConfiguration {
  private ApplicationContext applicationContext;
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Primary
  @Bean
  TaskRepository dualExecutionRepository(
      Properties properties,
      List<TaskRepository> allRepositories,
      DynamicConfigService dynamicConfigService,
      ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;

    allRepositories.forEach(repo -> log.info("Available TaskRepository: " + repo));

    TaskRepository primary =
        findTaskRepository(allRepositories, properties.primaryClass, properties.primaryName);
    TaskRepository previous =
        findTaskRepository(allRepositories, properties.previousClass, properties.previousName);
    return new DualTaskRepository(
        primary,
        previous,
        properties.executorThreadPoolSize,
        properties.executorTimeoutSeconds,
        dynamicConfigService);
  }

  private TaskRepository findTaskRepositoryByClass(
      List<TaskRepository> allRepositories, String className) {
    Class repositoryClass;
    try {
      repositoryClass = Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new BeanCreationException("Could not find TaskRepository class", e);
    }

    return allRepositories.stream()
        .filter(repositoryClass::isInstance)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    format("No TaskRepository bean of class %s found", repositoryClass)));
  }

  private TaskRepository findTaskRepository(
      List<TaskRepository> allRepositories, String beanClass, String beanName) {
    if (!Strings.isNullOrEmpty(beanName)) {
      return (TaskRepository) applicationContext.getBean(beanName);
    }

    return findTaskRepositoryByClass(allRepositories, beanClass);
  }

  @ConfigurationProperties("dual-task-repository")
  public static class Properties {
    /** The primary TaskRepository class&name. Only one is needed, name takes precedence . */
    String primaryClass;

    String primaryName;

    /** The previous TaskRepository class&name. Only one is needed, name takes precedence . */
    String previousClass;

    String previousName;

    /**
     * The number of threads that will be used for collating TaskRepository results from both
     * primary and previous backends. For list operations, two threads will be used.
     */
    int executorThreadPoolSize = 10;

    /**
     * The amount of time in seconds that async tasks will have to complete before being timed out.
     */
    long executorTimeoutSeconds = 10;

    public String getPrimaryClass() {
      return primaryClass;
    }

    public void setPrimaryClass(String primaryClass) {
      this.primaryClass = primaryClass;
    }

    public String getPrimaryName() {
      return primaryName;
    }

    public void setPrimaryName(String primaryName) {
      this.primaryName = primaryName;
    }

    public String getPreviousClass() {
      return previousClass;
    }

    public void setPreviousClass(String previousClass) {
      this.previousClass = previousClass;
    }

    public String getPreviousName() {
      return previousName;
    }

    public void setPreviousName(String previousName) {
      this.previousName = previousName;
    }

    public int getExecutorThreadPoolSize() {
      return executorThreadPoolSize;
    }

    public void setExecutorThreadPoolSize(int executorThreadPoolSize) {
      this.executorThreadPoolSize = executorThreadPoolSize;
    }

    public long getExecutorTimeoutSeconds() {
      return executorTimeoutSeconds;
    }

    public void setExecutorTimeoutSeconds(long executorTimeoutSeconds) {
      this.executorTimeoutSeconds = executorTimeoutSeconds;
    }
  }
}
