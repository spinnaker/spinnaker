/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.batch

import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.NoSuchJobException
import org.springframework.batch.support.PropertiesConverter
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.batch.SpringBatchActiveExecutionTracker.*

class SpringBatchActiveExecutionTrackerSpec extends Specification {

  def jobOperator = Stub(JobOperator)
  def currentInstance = "localhost"
  def redis = Mock(Jedis)
  @Subject tracker = new SpringBatchActiveExecutionTracker(
    jobOperator,
    currentInstance,
    [getResource: { -> redis }] as Pool
  )

  def "adds details for executions running on the current instance"() {
    given:
    jobOperator.jobNames >> jobs.keySet()
    jobs.each { name, ids ->
      jobOperator.getRunningExecutions(name) >> ids
      ids.each { id ->
        jobOperator.getParameters(id) >> PropertiesConverter.propertiesToString([orchestration: id.toString(), application: "app"] as Properties)
      }
    }

    when:
    tracker.recordRunningExecutions()

    then:
    1 * redis.sadd(KEY_INSTANCES, currentInstance)
    1 * redis.setex(tokenKeyFor(currentInstance), COUNT_TTL_SECONDS, "$expectedCount")
    1 * redis.sadd(executionsKeyFor(currentInstance), *_)
    1 * redis.expire(executionsKeyFor(currentInstance), EXECUTIONS_TTL_SECONDS)

    where:
    jobs = [job1: [1L, 2L], job2: [3L]]
    expectedCount = jobs.values().flatten().size()
  }

  def "handles stupid error from spring batch"() {
    given:
    jobOperator.jobNames >> (validJobs.keySet() + invalidJobName).sort()
    validJobs.each { name, ids ->
      jobOperator.getRunningExecutions(name) >> ids
      ids.each { id ->
        jobOperator.getParameters(id) >> PropertiesConverter.propertiesToString([orchestration: id.toString(), application: "app"] as Properties)
      }
    }
    jobOperator.getRunningExecutions(invalidJobName) >> { String name ->
      throw new NoSuchJobException(name)
    }

    when:
    tracker.recordRunningExecutions()

    then:
    1 * redis.sadd(KEY_INSTANCES, currentInstance)
    1 * redis.setex(tokenKeyFor(currentInstance), COUNT_TTL_SECONDS, "$expectedCount")

    where:
    validJobs = [job1: [1L, 2L], job3: [3L]]
    invalidJobName = "job2"
    expectedCount = validJobs.values().flatten().size()
  }

  @Unroll
  def "if #condition an empty set of executions is recorded"() {
    given:
    jobOperator.jobNames >> jobs
    jobOperator.getRunningExecutions(_) >> []

    when:
    tracker.recordRunningExecutions()

    then:
    1 * redis.sadd(KEY_INSTANCES, currentInstance)
    1 * redis.setex(tokenKeyFor(currentInstance), COUNT_TTL_SECONDS, "0")

    where:
    jobs             | condition
    []               | "no jobs exist"
    ["job1", "job2"] | "no jobs are running"
  }

  def "retrieving details expires instances from the set"() {
    given:
    redis.smembers(KEY_INSTANCES) >> ["active1", "active2", "inactive", "zombie"]
    redis.get(tokenKeyFor("active1")) >> "2"
    redis.get(tokenKeyFor("active2")) >> "1"
    redis.get(tokenKeyFor("inactive")) >> null // key expired and hasn't been updated
    redis.get(tokenKeyFor("zombie")) >> null // key expired and hasn't been updated
    redis.scard(executionsKeyFor("active1")) >> 2
    redis.scard(executionsKeyFor("active2")) >> 1
    redis.scard(executionsKeyFor("inactive")) >> 0
    redis.scard(executionsKeyFor("zombie")) >> 1
    redis.smembers(executionsKeyFor("active1")) >> ["spintest:pipeline:1", "spintest:pipeline:2"]
    redis.smembers(executionsKeyFor("active2")) >> ["spintest:pipeline:3"]
    redis.smembers(executionsKeyFor("inactive")) >> []
    redis.smembers(executionsKeyFor("zombie")) >> ["spintest:pipeline:4"]

    when:
    def result = tracker.activeExecutionsByInstance()

    then:
    result["active1"].count == 2
    !result["active1"].overdue
    result["active2"].count == 1
    !result["active2"].overdue
    result["inactive"].count == 0
    result["inactive"].overdue
    result["zombie"].count == 1
    result["zombie"].overdue

    and:
    1 * redis.srem(KEY_INSTANCES, "inactive")
    0 * redis.srem(KEY_INSTANCES, _)
  }

  def "can pull the count from an older instance that doesn't record details"() {
    given:
    redis.smembers(KEY_INSTANCES) >> ["legacy"]
    redis.get(tokenKeyFor("legacy")) >> "1"
    redis.scard(executionsKeyFor("legacy")) >> 0
    redis.smembers(executionsKeyFor("legacy")) >> null

    when:
    def result = tracker.activeExecutionsByInstance()

    then:
    result["legacy"].count == 1
    result["legacy"].executions.empty
  }
}
