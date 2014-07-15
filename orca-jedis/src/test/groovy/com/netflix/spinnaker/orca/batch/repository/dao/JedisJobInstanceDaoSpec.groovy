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

import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
class JedisJobInstanceDaoSpec extends Specification {

  def pool = new JedisPool(new JedisPoolConfig(), "localhost")
  @AutoCleanup def jedis = pool.resource
  @Subject jobInstanceDao = new JedisJobInstanceDao(jedis)
  def jobExecutionDao = new JedisJobExecutionDao(jedis)

  def cleanup() {
    jedis.flushDB()
  }

  def "createJobInstance assigns a unique id"() {
    given:
    def jobInstance1 = jobInstanceDao.createJobInstance("foo", new JobParameters())

    when:
    def jobInstance2 = jobInstanceDao.createJobInstance("foo", new JobParameters())

    then:
    jobInstance1.id != jobInstance2.id
  }

  def "getJobInstance by name and parameters"() {
    given:
    jobInstanceDao.createJobInstance("foo", new JobParameters(a: new JobParameter("a")))

    expect:
    jobInstanceDao.getJobInstance(name, parameters) == null ^ shouldBeFound

    where:
    name  | parameterMap     | shouldBeFound
    "foo" | [a: "a"]         | true
    "foo" | [a: "b"]         | false
    "foo" | [b: "a"]         | false
    "bar" | [a: "a"]         | false
    "foo" | [a: "a", b: "b"] | false
    "foo" | [:]              | false

    parameters = new JobParameters(parameterMap.collectEntries {
      [(it.key): new JobParameter(it.value)]
    })
  }

  def "getJobInstance by id"() {
    given:
    def jobInstance = jobInstanceDao.createJobInstance("foo", new JobParameters())

    expect:
    jobInstanceDao.getJobInstance(jobInstance.id) != null
  }

  def "getJobInstance by JobExecution id"() {
    given:
    def jobInstance = jobInstanceDao.createJobInstance("foo", parameters)
    def jobExecution = new JobExecution(jobInstance, parameters)

    and:
    jobExecutionDao.saveJobExecution(jobExecution)

    expect:
    jobInstanceDao.getJobInstance(jobExecution) != null

    where:
    parameters = new JobParameters()
  }

  def "getJobInstances by name and range"() {
    given:
    def jobInstanceA = jobInstanceDao.createJobInstance("foo", new JobParameters(a: new JobParameter("a")))
    def jobInstanceB = jobInstanceDao.createJobInstance("foo", new JobParameters(b: new JobParameter("b")))
    def jobInstanceC = jobInstanceDao.createJobInstance("foo", new JobParameters(c: new JobParameter("c")))

    expect:
    with(jobInstanceDao.getJobInstances(jobName, start, count)) {
      size() == expectedCount
    }

    where:
    jobName | start | count             | expectedCount
    "foo"   | 0     | Integer.MAX_VALUE | 3
    "foo"   | 3     | Integer.MAX_VALUE | 0
    "foo"   | 0     | 2                 | 2
    "foo"   | 2     | 2                 | 1
    "bar"   | 0     | Integer.MAX_VALUE | 0
  }

}
