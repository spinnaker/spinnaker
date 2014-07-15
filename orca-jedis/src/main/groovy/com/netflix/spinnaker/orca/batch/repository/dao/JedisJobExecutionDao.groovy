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
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.Jedis

@CompileStatic
class JedisJobExecutionDao implements JobExecutionDao {

  private final Jedis jedis
  private JobKeyGenerator<JobParameters> jobKeyGenerator = new DefaultJobKeyGenerator()

  @Autowired
  JedisJobExecutionDao(Jedis jedis) {
    this.jedis = jedis
  }

  @Override
  void saveJobExecution(JobExecution jobExecution) {
    jobExecution.id = jedis.incr("jobExecutionId")
    jobExecution.incrementVersion()
    def key = "jobExecution:$jobExecution.id"
    jedis.hset(key, "id", jobExecution.id.toString())
    jedis.hset(key, "version", jobExecution.version.toString())
    // TODO: all other fields

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
    throw new UnsupportedOperationException()
  }

  @Override
  void synchronizeStatus(JobExecution jobExecution) {
    throw new UnsupportedOperationException()
  }
}
