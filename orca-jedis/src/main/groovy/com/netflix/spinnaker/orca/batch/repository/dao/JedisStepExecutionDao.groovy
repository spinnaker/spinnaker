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
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.repository.dao.StepExecutionDao
import redis.clients.jedis.JedisCommands

import static com.netflix.spinnaker.orca.batch.repository.dao.DaoHelper.checkOptimisticLock
import static com.netflix.spinnaker.orca.batch.repository.dao.IsoTimestamp.deserializeDate
import static com.netflix.spinnaker.orca.batch.repository.dao.IsoTimestamp.serializeDate

@CompileStatic
class JedisStepExecutionDao implements StepExecutionDao {

  private final JedisCommands jedis

  JedisStepExecutionDao(JedisCommands jedis) {
    this.jedis = jedis
  }

  @Override
  void saveStepExecution(StepExecution stepExecution) {
    if (stepExecution.id != null) {
      throw new IllegalArgumentException("StepExecution is not expected to have an id (should not be saved yet)")
    }
    if (stepExecution.jobExecution.id == null) {
      throw new IllegalArgumentException("JobExecution must be saved already.")
    }
    stepExecution.id = jedis.incr("stepExecutionId")
    stepExecution.incrementVersion()

    def key = "stepExecution:$stepExecution.jobExecution.id:$stepExecution.id"
    storeStepExecution(key, stepExecution)
    indexStepExecutionByJobExecution(stepExecution)
  }

  @Override
  void saveStepExecutions(Collection<StepExecution> stepExecutions) {
    stepExecutions.each {
      saveStepExecution it
    }
  }

  @Override
  void updateStepExecution(StepExecution stepExecution) {
    if (stepExecution.id == null) {
      throw new IllegalArgumentException("step executions for given job execution are expected to be already saved")
    }

    def key = "stepExecution:$stepExecution.jobExecution.id:$stepExecution.id"

    if (!jedis.exists(key)) {
      throw new IllegalArgumentException("step execution is expected to be already saved")
    }

    checkOptimisticLock(jedis, key, stepExecution)
    stepExecution.incrementVersion()
    storeStepExecution(key, stepExecution)
  }

  @Override
  StepExecution getStepExecution(JobExecution jobExecution, Long stepExecutionId) {
    def hash = jedis.hgetAll("stepExecution:$jobExecution.id:$stepExecutionId")
    def stepExecution = new StepExecution(hash.stepName, jobExecution, hash.id as Long)
    stepExecution.status = BatchStatus.valueOf(hash.status)
    stepExecution.filterCount = hash.filterCount.toInteger()
    stepExecution.readCount = hash.readCount.toInteger()
    stepExecution.writeCount = hash.writeCount.toInteger()
    stepExecution.commitCount = hash.commitCount.toInteger()
    stepExecution.rollbackCount = hash.rollbackCount.toInteger()
    stepExecution.readSkipCount = hash.readSkipCount.toInteger()
    stepExecution.processSkipCount = hash.processSkipCount.toInteger()
    stepExecution.writeSkipCount = hash.writeSkipCount.toInteger()
    stepExecution.startTime = deserializeDate(hash.startTime)
    if (hash.endTime) {
      stepExecution.endTime = deserializeDate(hash.endTime)
    }
    if (hash.lastUpdated) {
      stepExecution.lastUpdated = deserializeDate(hash.lastUpdated)
    }
    stepExecution.exitStatus = new ExitStatus(hash.exitCode, hash.exitDescription)
    if (Boolean.parseBoolean(hash.terminateOnly)) {
      stepExecution.setTerminateOnly()
    }
    return stepExecution
  }

  @Override
  void addStepExecutions(JobExecution jobExecution) {
    jobExecution.addStepExecutions jedis.zrevrange("jobExecutionToStepExecutions:$jobExecution.id", Long.MIN_VALUE, Long.MAX_VALUE).collect {
      getStepExecution(jobExecution, it.toLong())
    }
  }

  private void storeStepExecution(GString key, StepExecution stepExecution) {
    jedis.hset(key, "id", stepExecution.id.toString())
    jedis.hset(key, "version", stepExecution.version.toString())
    jedis.hset(key, "stepName", stepExecution.stepName)
    jedis.hset(key, "status", stepExecution.status.name())
    jedis.hset(key, "filterCount", stepExecution.filterCount.toString())
    jedis.hset(key, "readCount", stepExecution.readCount.toString())
    jedis.hset(key, "writeCount", stepExecution.writeCount.toString())
    jedis.hset(key, "commitCount", stepExecution.commitCount.toString())
    jedis.hset(key, "rollbackCount", stepExecution.rollbackCount.toString())
    jedis.hset(key, "readSkipCount", stepExecution.readSkipCount.toString())
    jedis.hset(key, "processSkipCount", stepExecution.processSkipCount.toString())
    jedis.hset(key, "writeSkipCount", stepExecution.writeSkipCount.toString())
    jedis.hset(key, "startTime", serializeDate(stepExecution.startTime))
    if (stepExecution.endTime) {
      jedis.hset(key, "endTime", serializeDate(stepExecution.endTime))
    }
    if (stepExecution.lastUpdated) {
      jedis.hset(key, "lastUpdated", serializeDate(stepExecution.lastUpdated))
    }
    jedis.hset(key, "exitCode", stepExecution.exitStatus.exitCode)
    jedis.hset(key, "exitDescription", stepExecution.exitStatus.exitDescription)
    jedis.hset(key, "terminateOnly", stepExecution.terminateOnly.toString())
  }

  private void indexStepExecutionByJobExecution(StepExecution stepExecution) {
    jedis.zadd("jobExecutionToStepExecutions:$stepExecution.jobExecutionId", stepExecution.id, stepExecution.id.toString())
  }
}
