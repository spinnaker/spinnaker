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
import org.springframework.batch.core.*
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.JedisCommands

import static com.netflix.spinnaker.orca.batch.repository.dao.DaoHelper.checkOptimisticLock
import static com.netflix.spinnaker.orca.batch.repository.dao.IsoTimestamp.deserializeDate
import static com.netflix.spinnaker.orca.batch.repository.dao.IsoTimestamp.serializeDate

@CompileStatic
class JedisJobExecutionDao implements JobExecutionDao {

  private final JedisCommands jedis
  private JobInstanceDao jobInstanceDao
  private JobKeyGenerator<JobParameters> jobKeyGenerator = new DefaultJobKeyGenerator()

  @Autowired
  JedisJobExecutionDao(JedisCommands jedis, JobInstanceDao jobInstanceDao) {
    this.jedis = jedis
    this.jobInstanceDao = jobInstanceDao
  }

  @Override
  void saveJobExecution(JobExecution jobExecution) {
    if (jobExecution.id != null) {
      throw new IllegalArgumentException("JobExecution is not expected to have an id (should not be saved yet)")
    }
    if (jobExecution.jobId == null) {
      throw new IllegalArgumentException("JobExecution Job-Id cannot be null.")
    }

    jobExecution.id = jedis.incr("jobExecutionId")
    jobExecution.incrementVersion()

    def key = "jobExecution:$jobExecution.id"
    storeJobExecution(key, jobExecution)

    storeJobParameters(jobExecution)
  }

  @Override
  void updateJobExecution(JobExecution jobExecution) {
    if (jobExecution.id == null) {
      throw new IllegalArgumentException("JobExecution is expected to have an id (should be saved already)")
    }

    def key = "jobExecution:$jobExecution.id"

    if (!jedis.exists(key)) {
      throw new IllegalArgumentException("JobExecution must already be saved")
    }

    checkOptimisticLock(jedis, key, jobExecution)

    jobExecution.incrementVersion()
    storeJobExecution(key, jobExecution)
  }

  @Override
  List<JobExecution> findJobExecutions(JobInstance jobInstance) {
    jedis.zrange("jobInstanceExecutions:$jobInstance.id", 0, Long.MAX_VALUE).collect {
      getJobExecution it as Long
    }
  }

  @Override
  JobExecution getLastJobExecution(JobInstance jobInstance) {
    def set = jedis.zrevrange("jobInstanceExecutions:$jobInstance.id", 0, 1)
    if (set.empty) {
      null
    } else {
      def id = set.first()
      getJobExecution(id as Long)
    }
  }

  @Override
  Set<JobExecution> findRunningJobExecutions(String jobName) {
    jedis.smembers("runningJobExecutions:$jobName").collect {
      getJobExecution(it as Long)
    } as Set
  }

  @Override
  JobExecution getJobExecution(Long executionId) {
    def hash = jedis.hgetAll("jobExecution:$executionId")

    def jobInstance = jobInstanceDao.getJobInstance(hash.jobId as Long)

    def parameters = readJobParameters(executionId)

    def jobExecution = new JobExecution(jobInstance, hash.id as Long, parameters, hash.jobConfigurationName)
    if (hash.startTime) jobExecution.startTime = deserializeDate(hash.startTime)
    if (hash.endTime) jobExecution.endTime = deserializeDate(hash.endTime)
    if (hash.status) jobExecution.status = BatchStatus.valueOf(hash.status)
    jobExecution.exitStatus = new ExitStatus(hash.exitCode, hash.exitDescription)
    jobExecution.version = hash.version as Integer
    if (hash.createTime) jobExecution.createTime = deserializeDate(hash.createTime)
    if (hash.lastUpdated) jobExecution.lastUpdated = deserializeDate(hash.lastUpdated)

    return jobExecution
  }

  @Override
  void synchronizeStatus(JobExecution jobExecution) {
    jedis.hmget("jobExecution:$jobExecution.id", "status", "version").with {
      def version = last() as Integer
      if (version != jobExecution.version) {
        jobExecution.status = BatchStatus.valueOf(first())
        jobExecution.version = version
      }
    }
  }

  private void storeJobExecution(String key, JobExecution jobExecution) {
    jedis.hset(key, "id", jobExecution.id.toString())
    if (jobExecution.jobId) jedis.hset(key, "jobId", jobExecution.jobId.toString())
    if (jobExecution.startTime) jedis.hset(key, "startTime", serializeDate(jobExecution.startTime))
    if (jobExecution.endTime) jedis.hset(key, "endTime", serializeDate(jobExecution.endTime))
    jedis.hset(key, "status", jobExecution.status.name())
    jedis.hset(key, "exitCode", jobExecution.exitStatus.exitCode)
    jedis.hset(key, "exitDescription", jobExecution.exitStatus.exitDescription)
    jedis.hset(key, "version", jobExecution.version.toString())
    jedis.hset(key, "createTime", serializeDate(jobExecution.createTime))
    if (jobExecution.lastUpdated) jedis.hset(key, "lastUpdated", serializeDate(jobExecution.lastUpdated))
    if (jobExecution.jobConfigurationName) jedis.hset(key, "jobConfigurationName", jobExecution.jobConfigurationName)

    indexExecutionToJob(jobExecution)
    indexJobToExecutions(jobExecution)
    indexRunningExecutions(jobExecution)
  }

  private void indexExecutionToJob(JobExecution jobExecution) {
    def jobInstanceKey = "jobInstance:$jobExecution.jobInstance.jobName|${jobKeyGenerator.generateKey(jobExecution.jobParameters)}"
    jedis.set("jobExecutionToJobInstance:$jobExecution.id", jobInstanceKey)
  }

  private void indexJobToExecutions(JobExecution jobExecution) {
    jedis.zrem("jobInstanceExecutions:$jobExecution.jobId", jobExecution.id.toString())
    jedis.zadd("jobInstanceExecutions:$jobExecution.jobId", jobExecution.createTime.time, jobExecution.id.toString())
  }

  void indexRunningExecutions(JobExecution jobExecution) {
    def key = "runningJobExecutions:$jobExecution.jobInstance.jobName"
    if (jobExecution.isRunning()) {
      jedis.sadd(key, jobExecution.id.toString())
    } else {
      jedis.srem(key, jobExecution.id.toString())
    }
  }

  void storeJobParameters(JobExecution jobExecution) {
    jobExecution.jobParameters.parameters.each {
      String value
      switch (it.value.type) {
        case JobParameter.ParameterType.DATE:
          value = serializeDate(it.value.value as Date)
          break
        default:
          value = it.value.toString()
      }
      jedis.hset("jobExecutionParameters:$jobExecution.id", it.key, "$it.value.type|$value")
    }
  }

  private JobParameters readJobParameters(long executionId) {
    def parameters = new JobParametersBuilder()
    jedis.hgetAll("jobExecutionParameters:$executionId").each {
      def l = it.value.tokenize("|")
      def type = JobParameter.ParameterType.valueOf(l[0])
      def value = l[1]
      switch (type) {
        case JobParameter.ParameterType.DATE:
          parameters.addDate(it.key, deserializeDate(value))
          break
        case JobParameter.ParameterType.LONG:
          parameters.addLong(it.key, Long.valueOf(value))
          break
        case JobParameter.ParameterType.DOUBLE:
          parameters.addDouble(it.key, Double.valueOf(value))
          break
        default:
          parameters.addString(it.key, value)
      }
    }
    parameters.toJobParameters()
  }
}
