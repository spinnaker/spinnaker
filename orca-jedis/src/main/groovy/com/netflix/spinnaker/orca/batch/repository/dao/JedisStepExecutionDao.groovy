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

package com.netflix.spinnaker.orca.batch.repository.dao

import groovy.transform.CompileStatic
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.repository.dao.StepExecutionDao
import redis.clients.jedis.Jedis

@CompileStatic
class JedisStepExecutionDao implements StepExecutionDao {

  private final Jedis jedis

  JedisStepExecutionDao(Jedis jedis) {
    this.jedis = jedis
  }

  @Override
  void saveStepExecution(StepExecution stepExecution) {
    if (stepExecution.id != null) {
      throw new IllegalArgumentException("StepExecution is not expected to have an id (should not be saved yet)")
    }
//    Object[] parameterValues = new Object[] { stepExecution.getId(), stepExecution.getVersion(),
//      stepExecution.getStepName(), stepExecution.getJobExecutionId(), stepExecution.getStartTime(),
//      stepExecution.getEndTime(), stepExecution.getStatus().toString(), stepExecution.getCommitCount(),
//      stepExecution.getReadCount(), stepExecution.getFilterCount(), stepExecution.getWriteCount(),
//      stepExecution.getExitStatus().getExitCode(), exitDescription, stepExecution.getReadSkipCount(),
//      stepExecution.getWriteSkipCount(), stepExecution.getProcessSkipCount(),
//      stepExecution.getRollbackCount(), stepExecution.getLastUpdated() };
    stepExecution.id = jedis.incr("stepExecutionId")
    stepExecution.incrementVersion()
  }

  @Override
  void saveStepExecutions(Collection<StepExecution> stepExecutions) {
    throw new UnsupportedOperationException()
  }

  @Override
  void updateStepExecution(StepExecution stepExecution) {
    throw new UnsupportedOperationException()
  }

  @Override
  StepExecution getStepExecution(JobExecution jobExecution, Long stepExecutionId) {
    throw new UnsupportedOperationException()
  }

  @Override
  void addStepExecutions(JobExecution jobExecution) {
    throw new UnsupportedOperationException()
  }
}
