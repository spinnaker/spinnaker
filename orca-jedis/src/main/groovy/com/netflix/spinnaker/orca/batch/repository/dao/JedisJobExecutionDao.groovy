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
import redis.clients.jedis.Jedis

import java.text.SimpleDateFormat

@CompileStatic
class JedisJobExecutionDao implements JobExecutionDao {

  private final Jedis jedis
  private JobInstanceDao jobInstanceDao
  private JobKeyGenerator<JobParameters> jobKeyGenerator = new DefaultJobKeyGenerator()

  private final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  @Autowired
  JedisJobExecutionDao(Jedis jedis, JobInstanceDao jobInstanceDao) {
    this.jedis = jedis
    this.jobInstanceDao = jobInstanceDao
  }

  @Override
  void saveJobExecution(JobExecution jobExecution) {
    jobExecution.id = jedis.incr("jobExecutionId")
    jobExecution.incrementVersion()
    def key = "jobExecution:$jobExecution.id"
    jedis.hset(key, "id", jobExecution.id.toString())
    jedis.hset(key, "jobId", jobExecution.jobId.toString())
    if (jobExecution.startTime) jedis.hset(key, "startTime", jobExecution.startTime.format(TIMESTAMP_FORMAT))
    if (jobExecution.endTime) jedis.hset(key, "endTime", jobExecution.endTime?.format(TIMESTAMP_FORMAT))
    jedis.hset(key, "status", jobExecution.status.name())
    jedis.hset(key, "exitCode", jobExecution.exitStatus.exitCode)
    jedis.hset(key, "exitDescription", jobExecution.exitStatus.exitDescription)
    jedis.hset(key, "version", jobExecution.version.toString())
    jedis.hset(key, "createTime", jobExecution.createTime.format(TIMESTAMP_FORMAT))
    if (jobExecution.lastUpdated) jedis.hset(key, "lastUpdated", jobExecution.lastUpdated.format(TIMESTAMP_FORMAT))
    if (jobExecution.jobConfigurationName) jedis.hset(key, "jobConfigurationName", jobExecution.jobConfigurationName)

    def jobInstanceKey = "jobInstance:$jobExecution.jobInstance.jobName|${jobKeyGenerator.generateKey(jobExecution.jobParameters)}"
    jedis.set("jobExecutionToJobInstance:$jobExecution.id", jobInstanceKey)
  }

  @Override
  void updateJobExecution(JobExecution jobExecution) {
    throw new UnsupportedOperationException()
  }

  @Override
  List<JobExecution> findJobExecutions(JobInstance jobInstance) {
    throw new UnsupportedOperationException()
  }

  @Override
  JobExecution getLastJobExecution(JobInstance jobInstance) {
    throw new UnsupportedOperationException()
  }

  @Override
  Set<JobExecution> findRunningJobExecutions(String jobName) {
    throw new UnsupportedOperationException()
  }

  @Override
  JobExecution getJobExecution(Long executionId) {
    def hash = jedis.hgetAll("jobExecution:$executionId")

    def jobInstance = jobInstanceDao.getJobInstance(hash.jobId as Long)

    def jobExecution = new JobExecution(jobInstance, hash.id as Long, null, hash.jobConfigurationName)
    if (hash.startTime) jobExecution.startTime = new SimpleDateFormat(TIMESTAMP_FORMAT).parse(hash.startTime)
    if (hash.endTime) jobExecution.endTime = new SimpleDateFormat(TIMESTAMP_FORMAT).parse(hash.endTime)
    jobExecution.status = BatchStatus.valueOf(hash.status)
    jobExecution.exitStatus = new ExitStatus(hash.exitCode, hash.exitDescription)
    jobExecution.version = hash.version as Integer
    jobExecution.createTime = new SimpleDateFormat(TIMESTAMP_FORMAT).parse(hash.createTime)
    if (hash.lastUpdated) jobExecution.lastUpdated = new SimpleDateFormat(TIMESTAMP_FORMAT).parse(hash.lastUpdated)
    return jobExecution
  }

  @Override
  void synchronizeStatus(JobExecution jobExecution) {
    throw new UnsupportedOperationException()
  }
}
