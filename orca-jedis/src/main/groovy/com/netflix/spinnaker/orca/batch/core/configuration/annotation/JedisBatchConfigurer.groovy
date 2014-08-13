/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.core.configuration.annotation

import groovy.transform.CompileStatic
import javax.annotation.PostConstruct
import com.netflix.spinnaker.orca.batch.core.explore.support.JedisJobExplorerFactoryBean
import com.netflix.spinnaker.orca.batch.repository.support.JedisJobRepositoryFactoryBean
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import redis.clients.jedis.JedisCommands

@CompileStatic
@Component
class JedisBatchConfigurer implements BatchConfigurer {

  private final JedisCommands jedis
  private PlatformTransactionManager transactionManager
  private JobRepository jobRepository
  private JobLauncher jobLauncher
  private JobExplorer jobExplorer
  private JedisJobRepositoryFactoryBean repositoryFactory

  @Autowired
  JedisBatchConfigurer(JedisCommands jedis) {
    this.jedis = jedis
  }

  @Override
  JobRepository getJobRepository() {
    return jobRepository
  }

  @Override
  PlatformTransactionManager getTransactionManager() {
    return transactionManager
  }

  @Override
  JobLauncher getJobLauncher() {
    return jobLauncher
  }

  @Override
  JobExplorer getJobExplorer() {
    return jobExplorer
  }

  @PostConstruct
  void initialize() {
    if (!transactionManager) {
      transactionManager = new ResourcelessTransactionManager()
    }

    jobRepository = createJobRepository()
    jobLauncher = createJobLauncher()
    jobExplorer = createJobExplorer()
  }

  private JobLauncher createJobLauncher() {
    def jobLauncher = new SimpleJobLauncher()
    jobLauncher.jobRepository = jobRepository
    jobLauncher.afterPropertiesSet()
    return jobLauncher
  }

  private JobRepository createJobRepository() {
    repositoryFactory = new JedisJobRepositoryFactoryBean(jedis, transactionManager)
    repositoryFactory.afterPropertiesSet()
    return (JobRepository) repositoryFactory.object
    // cast attempts to avoid weird compilation problem on Jenkins with Java 1.7.0_07
  }

  private JobExplorer createJobExplorer() {
    def factory = new JedisJobExplorerFactoryBean(repositoryFactory)
    factory.afterPropertiesSet()
    factory.object
  }
}
