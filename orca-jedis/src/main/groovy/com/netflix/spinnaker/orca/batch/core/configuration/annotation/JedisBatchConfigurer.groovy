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
import com.netflix.spinnaker.orca.batch.repository.support.JedisJobRepositoryFactoryBean
import org.springframework.batch.core.configuration.annotation.BatchConfigurer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import redis.clients.jedis.JedisCommands

@Component
@CompileStatic
class JedisBatchConfigurer implements BatchConfigurer {

  private final JedisCommands jedis
  private PlatformTransactionManager transactionManager
  private JobRepository jobRepository
  private JobLauncher jobLauncher

  @Autowired
  JedisBatchConfigurer(JedisCommands jedis) {
    this.jedis = jedis
  }

  @Override
  public JobRepository getJobRepository() {
    return jobRepository
  }

  @Override
  public PlatformTransactionManager getTransactionManager() {
    return transactionManager
  }

  @Override
  public JobLauncher getJobLauncher() {
    return jobLauncher
  }

  @PostConstruct
  public void initialize() {
    if (!transactionManager) {
      transactionManager = new ResourcelessTransactionManager()
    }
    jobRepository = createJobRepository()
    jobLauncher = createJobLauncher()
  }

  private JobLauncher createJobLauncher() {
    def jobLauncher = new SimpleJobLauncher()
    jobLauncher.jobRepository = jobRepository
    jobLauncher.afterPropertiesSet()
    return jobLauncher
  }

  private JobRepository createJobRepository() {
    def factory = new JedisJobRepositoryFactoryBean(jedis, transactionManager)
    factory.afterPropertiesSet()
    return factory.object
  }
}
