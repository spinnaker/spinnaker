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

package com.netflix.spinnaker.orca.test.batch

import groovy.transform.CompileStatic
import com.netflix.spinnaker.kork.jedis.JedisConfig
import com.netflix.spinnaker.orca.batch.core.configuration.annotation.JedisBatchConfigurer
import com.netflix.spinnaker.orca.batch.core.explore.support.JedisJobExplorerFactoryBean
import org.springframework.batch.core.configuration.ListableJobLocator
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.SimpleJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import redis.clients.jedis.JedisCommands

/**
 * This is a bare-bones configuration for running end-to-end Spring batch tests.
 */
@Configuration
@EnableBatchProcessing
// TODO: this probably shouldn't be directly imported as it's conditional
@Import(JedisConfig)
@CompileStatic
class BatchTestConfiguration {

  // required for the configuration from JedisConfig to work properly
  @Bean PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() {
    new PropertyPlaceholderConfigurer()
  }

  @Bean BatchConfigurer batchConfigurer(JedisCommands jedis) {
    new JedisBatchConfigurer(jedis)
  }

  @Bean
  JobOperator jobOperator(JobLauncher jobLauncher, JobRepository jobRepository, JobExplorer jobExplorer, ListableJobLocator jobRegistry) {
    def jobOperator = new SimpleJobOperator()
    jobOperator.jobLauncher = jobLauncher
    jobOperator.jobRepository = jobRepository
    jobOperator.jobExplorer = jobExplorer
    jobOperator.jobRegistry = jobRegistry
    return jobOperator
  }
}
