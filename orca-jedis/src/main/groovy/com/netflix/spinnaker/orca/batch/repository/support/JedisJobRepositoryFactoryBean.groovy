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

package com.netflix.spinnaker.orca.batch.repository.support

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.batch.repository.dao.JedisExecutionContextDao
import com.netflix.spinnaker.orca.batch.repository.dao.JedisJobExecutionDao
import com.netflix.spinnaker.orca.batch.repository.dao.JedisJobInstanceDao
import com.netflix.spinnaker.orca.batch.repository.dao.JedisStepExecutionDao
import org.springframework.batch.core.repository.dao.ExecutionContextDao
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.batch.core.repository.dao.StepExecutionDao
import org.springframework.batch.core.repository.support.AbstractJobRepositoryFactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import redis.clients.jedis.JedisCommands

@CompileStatic
class JedisJobRepositoryFactoryBean extends AbstractJobRepositoryFactoryBean {

  private final JedisCommands jedis

  private JobInstanceDao jobInstanceDao
  private JobExecutionDao jobExecutionDao
  private StepExecutionDao stepExecutionDao
  private ExecutionContextDao executionContextDao

  @Autowired
  JedisJobRepositoryFactoryBean(JedisCommands jedis, PlatformTransactionManager transactionManager) {
    this.jedis = jedis
    this.transactionManager = transactionManager
  }

  @Override
  void afterPropertiesSet() throws Exception {
    jobInstanceDao = new JedisJobInstanceDao(jedis)
    jobExecutionDao = new JedisJobExecutionDao(jedis, jobInstanceDao)
    stepExecutionDao = new JedisStepExecutionDao(jedis)
    executionContextDao = new JedisExecutionContextDao(jedis)
    super.afterPropertiesSet()
  }

  @Override
  protected JobInstanceDao createJobInstanceDao() {
    jobInstanceDao
  }

  @Override
  protected JobExecutionDao createJobExecutionDao() {
    jobExecutionDao
  }

  @Override
  protected StepExecutionDao createStepExecutionDao() {
    stepExecutionDao
  }

  @Override
  protected ExecutionContextDao createExecutionContextDao() {
    executionContextDao
  }

  JobInstanceDao getJobInstanceDao() {
    return jobInstanceDao
  }

  JobExecutionDao getJobExecutionDao() {
    return jobExecutionDao
  }

  StepExecutionDao getStepExecutionDao() {
    return stepExecutionDao
  }

  ExecutionContextDao getExecutionContextDao() {
    return executionContextDao
  }
}
