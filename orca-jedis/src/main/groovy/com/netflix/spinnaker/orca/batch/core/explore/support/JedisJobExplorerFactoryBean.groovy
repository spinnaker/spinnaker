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

package com.netflix.spinnaker.orca.batch.core.explore.support

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.batch.repository.support.JedisJobRepositoryFactoryBean
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.explore.support.AbstractJobExplorerFactoryBean
import org.springframework.batch.core.explore.support.SimpleJobExplorer
import org.springframework.batch.core.repository.dao.ExecutionContextDao
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.batch.core.repository.dao.StepExecutionDao
import org.springframework.beans.factory.InitializingBean

@CompileStatic
class JedisJobExplorerFactoryBean extends AbstractJobExplorerFactoryBean implements InitializingBean {

  private final JedisJobRepositoryFactoryBean repositoryFactory

  private JobExplorer jobExplorer

  JedisJobExplorerFactoryBean(JedisJobRepositoryFactoryBean repositoryFactory) {
    this.repositoryFactory = repositoryFactory
  }

  @Override
  void afterPropertiesSet() {
    jobExplorer = new SimpleJobExplorer(createJobInstanceDao(), createJobExecutionDao(), createStepExecutionDao(), createExecutionContextDao())
  }

  @Override
  protected JobInstanceDao createJobInstanceDao() {
    repositoryFactory.jobInstanceDao
  }

  @Override
  protected JobExecutionDao createJobExecutionDao() {
    repositoryFactory.jobExecutionDao
  }

  @Override
  protected StepExecutionDao createStepExecutionDao() {
    repositoryFactory.stepExecutionDao
  }

  @Override
  protected ExecutionContextDao createExecutionContextDao() {
    repositoryFactory.executionContextDao
  }

  @Override
  JobExplorer getObject() {
    jobExplorer
  }
}
