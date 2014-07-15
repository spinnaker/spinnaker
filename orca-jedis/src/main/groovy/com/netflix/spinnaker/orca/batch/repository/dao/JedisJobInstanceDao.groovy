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
import org.springframework.batch.core.launch.NoSuchJobException
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.Jedis

@CompileStatic
class JedisJobInstanceDao implements JobInstanceDao {

  private final Jedis jedis
  private JobKeyGenerator<JobParameters> jobKeyGenerator = new DefaultJobKeyGenerator()

  @Autowired
  JedisJobInstanceDao(Jedis jedis) {
    this.jedis = jedis
  }

  @Override
  JobInstance createJobInstance(String jobName, JobParameters jobParameters) {
    def jobInstance = new JobInstance(1L, jobName)
    def key = "jobInstance:$jobName|${jobKeyGenerator.generateKey(jobParameters)}"
    jedis.hset(key, "id", jobInstance.id.toString())
    jedis.hset(key, "jobName", jobInstance.jobName)
    // TODO: do we need to handle version
    return jobInstance
  }

  @Override
  JobInstance getJobInstance(String jobName, JobParameters jobParameters) {
    def key = "jobInstance:$jobName|${jobKeyGenerator.generateKey(jobParameters)}"
    Map<String, String> map = jedis.hgetAll(key)
    map ? new JobInstance(map.id as Long, map.jobName) : null
  }

  @Override
  JobInstance getJobInstance(Long instanceId) {
    throw new UnsupportedOperationException()
//    jedis.
  }

  @Override
  JobInstance getJobInstance(JobExecution jobExecution) {
    throw new UnsupportedOperationException()
  }

  @Override
  List<JobInstance> getJobInstances(String jobName, int start, int count) {
    throw new UnsupportedOperationException()
  }

  @Override
  List<String> getJobNames() {
    throw new UnsupportedOperationException()
  }

  @Override
  List<JobInstance> findJobInstancesByName(String jobName, int start, int count) {
    throw new UnsupportedOperationException()
  }

  @Override
  int getJobInstanceCount(String jobName) throws NoSuchJobException {
    throw new UnsupportedOperationException()
  }
}
