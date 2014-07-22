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
import org.springframework.batch.core.launch.NoSuchJobException
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.batch.BatchHelpers.noParameters
import static com.netflix.spinnaker.orca.batch.BatchHelpers.toJobParameters
import static org.hamcrest.Matchers.containsInAnyOrder
import static spock.util.matcher.HamcrestSupport.that

abstract class JobInstanceDaoTck extends Specification {

  @Subject JobInstanceDao jobInstanceDao
  JobExecutionDao jobExecutionDao

  def setup() {
    jobInstanceDao = createJobInstanceDao()
    jobExecutionDao = createJobExecutionDao(jobInstanceDao)
  }

  abstract JobInstanceDao createJobInstanceDao()

  abstract JobExecutionDao createJobExecutionDao(JobInstanceDao jobInstanceDao)

  def "createJobInstance assigns a unique id"() {
    given:
    def jobInstance1 = jobInstanceDao.createJobInstance("foo", new JobParameters(a: new JobParameter("a")))

    when:
    def jobInstance2 = jobInstanceDao.createJobInstance("foo", new JobParameters(b: new JobParameter("b")))

    then:
    jobInstance1.id != jobInstance2.id
  }

  @Unroll("createJobInstance does not allow a second instance of the job '#jobName' with parameters #parameterMap")
  def "createJobInstance does not allow multiple instances of a job with the same parameters"() {
    given:
    jobInstanceDao.createJobInstance(jobName, parameters)

    when:
    jobInstanceDao.createJobInstance(jobName, parameters)

    then:
    thrown IllegalStateException

    where:
    parameterMap     | _
    [:]              | _
    [a: "a"]         | _
    [a: "a", b: "b"] | _

    jobName = "foo"
    parameters = toJobParameters(parameterMap)
  }

  @Unroll("getJobInstance #expectation a job using job name '#jobName' and parameters #parameterMap")
  def "getJobInstance by name and parameters"() {
    given:
    jobInstanceDao.createJobInstance("foo", new JobParameters(a: new JobParameter("a")))

    expect:
    jobInstanceDao.getJobInstance(jobName, parameters) == null ^ shouldBeFound

    where:
    jobName | parameterMap     | shouldBeFound
    "foo"   | [a: "a"]         | true
    "foo"   | [a: "b"]         | false
    "foo"   | [b: "a"]         | false
    "bar"   | [a: "a"]         | false
    "foo"   | [a: "a", b: "b"] | false
    "foo"   | [:]              | false

    parameters = toJobParameters(parameterMap)
    expectation = shouldBeFound ? "should find" : "should not find"
  }

  def "getJobInstance hydrates JobInstance correctly"() {
    given:
    jobInstanceDao.createJobInstance(jobName, parameters)

    expect:
    def jobInstance = jobInstanceDao.getJobInstance(jobName, parameters)
    jobInstance.id >= 0
    jobInstance.jobName == jobName
    jobInstance.version == 0

    where:
    jobName = "foo"
    parameters = noParameters()
  }

  def "getJobInstance by id"() {
    given:
    def jobInstance = jobInstanceDao.createJobInstance("foo", noParameters())

    expect:
    jobInstanceDao.getJobInstance(jobInstance.id) != null
  }

  def "getJobInstance by id returns null if the id is not found"() {
    expect:
    jobInstanceDao.getJobInstance(1L) == null
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
    parameters = noParameters()
  }

  def "getJobInstance using JobExecution returns null if there is no associated instance"() {
    given:
    def jobExecution = new JobExecution(1L)

    expect:
    jobInstanceDao.getJobInstance(jobExecution) == null
  }

  @Unroll("getJobInstances returns #expectedCount instances for jobName '#jobName', start #start and count #count")
  def "getJobInstances by name and range"() {
    given:
    jobInstanceDao.createJobInstance("foo", new JobParameters(a: new JobParameter("a")))
    jobInstanceDao.createJobInstance("foo", new JobParameters(b: new JobParameter("b")))
    jobInstanceDao.createJobInstance("foo", new JobParameters(c: new JobParameter("c")))

    expect:
    with(jobInstanceDao.getJobInstances(jobName, start, count)) {
      size() == expectedCount
    }

    where:
    jobName | start | count | expectedCount
    "foo"   | 0     | 99    | 3
    "foo"   | 0     | 2     | 2
    "foo"   | 2     | 2     | 1
    "foo"   | 3     | 99    | 0
    "bar"   | 0     | 99    | 0
  }

  def "getJobInstances results are ordered by id descending"() {
    given:
    jobInstanceDao.createJobInstance("foo", new JobParameters(a: new JobParameter("a")))
    jobInstanceDao.createJobInstance("foo", new JobParameters(b: new JobParameter("b")))
    jobInstanceDao.createJobInstance("foo", new JobParameters(c: new JobParameter("c")))

    expect:
    with(jobInstanceDao.getJobInstances("foo", 0, 3)) {
      get(0).id > get(1).id
      get(1).id > get(2).id
    }
  }

  def "getJobNames returns all known job names in alphabetical order"() {
    given:
    jobNames.each {
      jobInstanceDao.createJobInstance(it, noParameters())
    }

    expect:
    jobInstanceDao.getJobNames() == jobNames.sort()

    where:
    jobNames = ["foo", "bar", "baz"]
  }

  @Unroll("findJobInstancesByName finds #expectedNames using the expression '#jobName'")
  def "findJobInstancesByName accepts wildcards"() {
    given:
    jobInstanceDao.createJobInstance("bar", new JobParameters(a: new JobParameter("a")))
    jobInstanceDao.createJobInstance("bar", new JobParameters(b: new JobParameter("b")))
    jobInstanceDao.createJobInstance("baz", noParameters())
    jobInstanceDao.createJobInstance("foo", noParameters())

    expect:
    jobInstanceDao.findJobInstancesByName(jobName, 0, Integer.MAX_VALUE).jobName.sort() == expectedNames.sort()

    where:
    jobName | expectedNames
    "baz"   | ["baz"]
    "bar"   | ["bar", "bar"]
    "b*"    | ["baz", "bar", "bar"]
    "*b*"   | ["baz", "bar", "bar"]
    "ba*"   | ["baz", "bar", "bar"]
    "a*"    | []
    "*a*"   | ["baz", "bar", "bar"]
  }

  @Unroll("findJobInstancesByName returns #expectedNames using the expression '#jobName', start #start and count #count")
  def "findJobInstancesByName limits results to the specified range"() {
    given:
    jobInstanceDao.createJobInstance("bar", toJobParameters(a: "a"))
    jobInstanceDao.createJobInstance("bar", toJobParameters(b: "b"))
    jobInstanceDao.createJobInstance("baz", noParameters())
    jobInstanceDao.createJobInstance("foo", noParameters())

    expect:
    that jobInstanceDao.findJobInstancesByName(jobName, start, count).jobName, containsInAnyOrder(*expectedNames)

    where:
    jobName | start | count | expectedNames
    "b*"    | 0     | 99    | ["baz", "bar", "bar"]
    "b*"    | 0     | 2     | ["baz", "bar"]
    "b*"    | 1     | 2     | ["bar", "bar"]
  }

  def "findJobInstancesByName orders results highest id first"() {
    given:
    (1..10).each { i ->
      jobNames.each { jobName ->
        jobInstanceDao.createJobInstance(jobName, toJobParameters(a: i))
      }
    }

    when:
    def jobInstances = jobInstanceDao.findJobInstancesByName(pattern, 0, 99)

    then:
    jobInstances.id == jobInstances.id.sort().reverse()

    where:
    jobNames = ["foo", "bar", "baz"]
    pattern = "b*"
  }

  @Unroll("getJobInstanceCount returns #expectedCount for the job name '#jobName'")
  def "getJobInstanceCount returns count by job name"() {
    given:
    jobInstanceDao.createJobInstance("foo", noParameters())
    jobInstanceDao.createJobInstance("bar", noParameters())
    jobInstanceDao.createJobInstance("baz", new JobParameters(a: new JobParameter("a")))
    jobInstanceDao.createJobInstance("baz", new JobParameters(b: new JobParameter("b")))

    expect:
    jobInstanceDao.getJobInstanceCount(jobName) == expectedCount

    where:
    jobName | expectedCount
    "foo"   | 1
    "bar"   | 1
    "baz"   | 2
  }

  def "getJobInstanceCount throws an exception for an invalid job name"() {
    when:
    jobInstanceDao.getJobInstanceCount("foo")

    then:
    thrown NoSuchJobException
  }
}
