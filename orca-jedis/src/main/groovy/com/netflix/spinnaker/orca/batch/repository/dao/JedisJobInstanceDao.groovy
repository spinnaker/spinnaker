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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.batch.core.*
import org.springframework.batch.core.launch.NoSuchJobException
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.JedisCommands

import java.util.regex.Pattern

@CompileStatic
class JedisJobInstanceDao implements JobInstanceDao {

  private final JedisCommands jedis
  private JobKeyGenerator<JobParameters> jobKeyGenerator = new DefaultJobKeyGenerator()

  @Autowired
  JedisJobInstanceDao(JedisCommands jedis) {
    this.jedis = jedis
  }

  @Override
  JobInstance createJobInstance(String jobName, JobParameters jobParameters) {
    def jobInstance = new JobInstance(jedis.incr("jobInstanceId"), jobName)
    jobInstance.incrementVersion()
    def key = "jobInstance:$jobName|${jobKeyGenerator.generateKey(jobParameters)}"

    if (jedis.exists(key)) {
      throw new IllegalStateException("JobInstance must not already exist")
    }

    jedis.hset(key, "id", jobInstance.id.toString())
    jedis.hset(key, "version", jobInstance.version.toString())
    jedis.hset(key, "jobName", jobInstance.jobName)

    indexJobById(jobInstance, key)
    indexJobByName(jobInstance, key)
    indexJobNames(jobInstance)
    return jobInstance
  }

  @Override
  JobInstance getJobInstance(String jobName, JobParameters jobParameters) {
    getJobInstanceByKey "jobInstance:$jobName|${jobKeyGenerator.generateKey(jobParameters)}"
  }

  @Override
  JobInstance getJobInstance(Long instanceId) {
    def key = jedis.get("jobInstanceId:$instanceId")
    key ? getJobInstanceByKey(key) : null
  }

  @Override
  JobInstance getJobInstance(JobExecution jobExecution) {
    def key = jedis.get("jobExecutionToJobInstance:$jobExecution.id")
    key ? getJobInstanceByKey(key) : null
  }

  @Override
  List<JobInstance> getJobInstances(String jobName, int start, int count) {
    jedis.zrevrange("jobInstanceName:$jobName", start, start + (count - 1)).collect {
      getJobInstanceByKey(it)
    }
  }

  @Override
  List<String> getJobNames() {
    jedis.smembers("jobInstanceNames").sort()
  }

  @CompileDynamic // have to do this due to https://jira.codehaus.org/browse/GROOVY-6961
  @Override
  List<JobInstance> findJobInstancesByName(String jobName, int start, int count) {
    // Obviously this isn't a great implementation as it's finding and hydrating way more JobInstance values than it
    // needs to. It's not advisable to use the KEYS command which could do the glob match as it blocks the entire Redis
    // cluster. There could be some optimization done by storing ids instead of keys in the jobInstanceName:foo set
    // which would mean it wouldn't be necessary to hydrate JobInstances just to do the ordering.
    def pattern = Pattern.compile(jobName.replaceAll(/\*/, ".*").replaceAll(/\?/, "."))
    def jobInstances = jedis.smembers("jobInstanceNames").findAll {
      it ==~ pattern
    } collectMany { String name ->
      jedis.zrevrange("jobInstanceName:$name", Long.MIN_VALUE, Long.MAX_VALUE).collect { key ->
        getJobInstanceByKey key
      }
    } sort { JobInstance it ->
      -it.id
    }
    jobInstances.subList(start, [start + count, jobInstances.size()].min())
  }

  @Override
  int getJobInstanceCount(String jobName) throws NoSuchJobException {
    if (!jedis.exists("jobInstanceName:$jobName")) {
      throw new NoSuchJobException("No job instances for job name $jobName were found")
    }
    jedis.zcount("jobInstanceName:$jobName", Long.MIN_VALUE, Long.MAX_VALUE)
  }

  private JobInstance getJobInstanceByKey(String key) {
    JobInstance jobInstance = null
    def hash = jedis.hgetAll(key)
    if (hash) {
      jobInstance = new JobInstance(hash.id as Long, hash.jobName)
      jobInstance.version = hash.version as Integer
    }
    return jobInstance
  }

  private String indexJobById(JobInstance jobInstance, String key) {
    jedis.set("jobInstanceId:$jobInstance.id", key)
  }

  private long indexJobByName(JobInstance jobInstance, String key) {
    jedis.zadd("jobInstanceName:$jobInstance.jobName", jobInstance.id, key)
  }

  private long indexJobNames(JobInstance jobInstance) {
    jedis.sadd("jobInstanceNames", jobInstance.jobName)
  }

}
