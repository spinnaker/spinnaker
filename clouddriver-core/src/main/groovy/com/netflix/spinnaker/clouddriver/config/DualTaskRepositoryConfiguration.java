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

import com.netflix.spinnaker.clouddriver.data.task.DualTaskRepository;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static com.netflix.spinnaker.clouddriver.config.DualTaskRepositoryConfiguration.Properties;
import static java.lang.String.format;

@Configuration
@ConditionalOnProperty("dualTaskRepository.enabled")
@EnableConfigurationProperties(Properties.class)
public class DualTaskRepositoryConfiguration {

  @Primary
  @Bean
  TaskRepository dualExecutionRepository(Properties properties, List<TaskRepository> allRepositories) {
    TaskRepository primary = findTaskRepositoryByClass(allRepositories, properties.primaryClass);
    TaskRepository previous = findTaskRepositoryByClass(allRepositories, properties.previousClass);
    return new DualTaskRepository(
      primary,
      previous,
      properties.executorThreadPoolSize,
      properties.executorTimeoutSeconds
    );
  }

  private TaskRepository findTaskRepositoryByClass(List<TaskRepository> allRepositories, String className) {
    Class repositoryClass;
    try {
      repositoryClass = Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new BeanCreationException("Could not find TaskRepository class", e);
    }

    return allRepositories
      .stream()
      .filter(repositoryClass::isInstance)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException(format(
        "No TaskRepository bean of class %s found", repositoryClass
      )));
  }

  @ConfigurationProperties("dualTaskRepository")
  public static class Properties {
    /**
     * The primary TaskRepository class.
     */
    String primaryClass;

    /**
     * The previous TaskRepository class.
     */
    String previousClass;

    /**
     * The number of threads that will be used for collating TaskRepository results from both primary and previous
     * backends. For list operations, two threads will be used.
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

    public String getPreviousClass() {
      return previousClass;
    }

    public void setPreviousClass(String previousClass) {
      this.previousClass = previousClass;
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
