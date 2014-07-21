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

import com.google.common.collect.Maps
import groovy.transform.CompileStatic
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.repository.dao.ExecutionContextDao
import org.springframework.batch.item.ExecutionContext
import redis.clients.jedis.JedisCommands

import static javax.xml.bind.DatatypeConverter.parseBase64Binary
import static javax.xml.bind.DatatypeConverter.printBase64Binary
import static org.springframework.util.SerializationUtils.deserialize
import static org.springframework.util.SerializationUtils.serialize

@CompileStatic
class JedisExecutionContextDao implements ExecutionContextDao {

  private final JedisCommands jedis

  JedisExecutionContextDao(JedisCommands jedis) {
    this.jedis = jedis
  }

  @Override
  ExecutionContext getExecutionContext(JobExecution jobExecution) {
    readExecutionContext "jobExecutionContext:$jobExecution.id"
  }

  @Override
  ExecutionContext getExecutionContext(StepExecution stepExecution) {
    readExecutionContext "stepExecutionContext:$stepExecution.id"
  }

  @Override
  void saveExecutionContext(JobExecution jobExecution) {
    writeExecutionContext "jobExecutionContext:$jobExecution.id", jobExecution.executionContext
  }

  @Override
  void saveExecutionContext(StepExecution stepExecution) {
    writeExecutionContext "stepExecutionContext:$stepExecution.id", stepExecution.executionContext
  }

  @Override
  void saveExecutionContexts(Collection<StepExecution> stepExecutions) {
    stepExecutions.each {
      saveExecutionContext it
    }
  }

  @Override
  void updateExecutionContext(JobExecution jobExecution) {
    _updateExecutionContext "jobExecutionContext:$jobExecution.id", jobExecution.executionContext
  }

  @Override
  void updateExecutionContext(StepExecution stepExecution) {
    _updateExecutionContext "stepExecutionContext:$stepExecution.id", stepExecution.executionContext
  }

  private ExecutionContext readExecutionContext(String key) {
    Map<String, Object> hash = Maps.transformValues(jedis.hgetAll(key)) {
      deserialize(parseBase64Binary(it))
    }
    new ExecutionContext(hash)
  }

  private void writeExecutionContext(String key, ExecutionContext executionContext) {
    executionContext.entrySet().each {
      jedis.hset key, it.key, printBase64Binary(serialize(it.value))
    }
  }

  private void _updateExecutionContext(String key, ExecutionContext executionContext) {
    jedis.del key
    writeExecutionContext key, executionContext
  }
}
