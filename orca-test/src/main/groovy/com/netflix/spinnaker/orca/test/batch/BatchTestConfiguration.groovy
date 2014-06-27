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

import javax.sql.DataSource
import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.ListableJobLocator
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.SimpleJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

/**
 * This is a bare-bones configuration for running end-to-end Spring batch tests.
 */
@Configuration
@EnableBatchProcessing
@PropertySource("classpath:batch.properties")
@CompileStatic
class BatchTestConfiguration {

  @Autowired private Environment env
  @Autowired private ResourceLoader resourceLoader

  @Bean(destroyMethod = "destroy")
  DataSource dataSource() {
    def ds = new SingleConnectionDataSource()
    ds.driverClassName = env.getProperty("batch.jdbc.driver")
    ds.url = env.getProperty("batch.jdbc.url")
    ds.username = env.getProperty("batch.jdbc.user")
    ds.password = env.getProperty("batch.jdbc.password")

    def populator = new ResourceDatabasePopulator()
    populator.addScript(resourceLoader.getResource(env.getProperty("batch.schema.script")))
    DatabasePopulatorUtils.execute(populator, ds)

    return ds
  }

  @Bean JobExplorerFactoryBean jobExplorerFactoryBean(DataSource dataSource) {
    new JobExplorerFactoryBean(dataSource: dataSource)
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
