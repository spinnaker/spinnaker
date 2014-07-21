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
import org.springframework.batch.core.repository.dao.ExecutionContextDao
import org.springframework.batch.item.ExecutionContext
import redis.clients.jedis.JedisCommands

@CompileStatic
class JedisExecutionContextDao implements ExecutionContextDao {

  private final JedisCommands jedis

  JedisExecutionContextDao(JedisCommands jedis) {
    this.jedis = jedis
  }

  @Override
  ExecutionContext getExecutionContext(JobExecution jobExecution) {
    _getExecutionContext "jobExecutionContext:$jobExecution.id"
  }

  @Override
  ExecutionContext getExecutionContext(StepExecution stepExecution) {
    _getExecutionContext "stepExecutionContext:$stepExecution.id"
  }

  @Override
  void saveExecutionContext(JobExecution jobExecution) {
    _saveExecutionContext "jobExecutionContext:$jobExecution.id", jobExecution.executionContext
  }

  @Override
  void saveExecutionContext(StepExecution stepExecution) {
    _saveExecutionContext "stepExecutionContext:$stepExecution.id", stepExecution.executionContext
  }

  @Override
  void saveExecutionContexts(Collection<StepExecution> stepExecutions) {
    throw new UnsupportedOperationException()
  }

  @Override
  void updateExecutionContext(JobExecution jobExecution) {
    throw new UnsupportedOperationException()
  }

  @Override
  void updateExecutionContext(StepExecution stepExecution) {
    throw new UnsupportedOperationException()
  }

  private ExecutionContext _getExecutionContext(String key) {
    def hash = jedis.hgetAll(key) as Map<String, Object>
    new ExecutionContext(hash)
  }

  private void _saveExecutionContext(String key, ExecutionContext executionContext) {
    executionContext.entrySet().each {
      jedis.hset(key, it.key, it.value.toString())
    }
  }
}
