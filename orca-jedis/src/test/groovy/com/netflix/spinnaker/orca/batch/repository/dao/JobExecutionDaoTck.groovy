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

import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.batch.repository.dao.BatchHelpers.noParameters

abstract class JobExecutionDaoTck extends Specification {

  @Subject JobExecutionDao jobExecutionDao
  JobInstanceDao jobInstanceDao

  JobInstance jobInstance

  abstract JobExecutionDao createJobExecutionDao(JobInstanceDao jobInstanceDao)

  abstract JobInstanceDao createJobInstanceDao()

  def setup() {
    jobInstanceDao = createJobInstanceDao()
    jobExecutionDao = createJobExecutionDao(jobInstanceDao)
    jobInstance = jobInstanceDao.createJobInstance("foo", noParameters())
  }

  @Unroll("saveJobExecution stores field values correctly with #description")
  def "saveJobExecution stores field values correctly"() {
    given:
    def jobExecution = new JobExecution(jobInstance, noParameters(), fields.jobConfigurationName)

    and:
    fields.each {
      jobExecution.properties[it.key] = it.value
    }

    when:
    jobExecutionDao.saveJobExecution(jobExecution)

    then:
    with(jobExecutionDao.getJobExecution(jobExecution.id)) {
      id == jobExecution.id
      jobId == jobExecution.jobId
      startTime == jobExecution.startTime
      endTime == jobExecution.endTime
      status == jobExecution.status
      exitStatus == jobExecution.exitStatus
      version == jobExecution.version
      createTime == jobExecution.createTime
      lastUpdated == jobExecution.lastUpdated
      jobConfigurationName == jobExecution.jobConfigurationName
    }

    where:
    fields                                     | _
    [:]                                        | _
    [startTime: new Date()]                    | _
    [endTime: new Date()]                      | _
    [lastUpdated: new Date()]                  | _
    [jobConfigurationName: "fooConfiguration"] | _

    description = fields.isEmpty() ? "no optional fields" : "the optional fields ${fields.keySet()}"
  }

  def "saveJobExecution assigns an id"() {
    given:
    def jobExecution = new JobExecution(jobInstance, noParameters())

    when:
    jobExecutionDao.saveJobExecution(jobExecution)

    then:
    jobExecution.id != null
  }

  def "saveJobExecution assigns a version"() {
    given:
    def jobExecution = new JobExecution(jobInstance, noParameters())

    when:
    jobExecutionDao.saveJobExecution(jobExecution)

    then:
    jobExecution.version == 0
  }

  def "saveJobExecution rejects a JobExecution with no JobInstance"() {
    given:
    def jobExecution = new JobExecution(new JobInstance(null, "foo"), noParameters())

    when:
    jobExecutionDao.saveJobExecution(jobExecution)

    then:
    thrown IllegalArgumentException
  }

  def "saveJobExecution rejects a JobExecution that has already been saved"() {
    given:
    def jobExecution = new JobExecution(jobInstance, noParameters())

    and:
    jobExecutionDao.saveJobExecution(jobExecution)

    when:
    jobExecutionDao.saveJobExecution(jobExecution)

    then:
    thrown IllegalArgumentException
  }

  def "updateJobExecution updates fields"() {
    given:
    def jobExecution = new JobExecution(jobInstance, noParameters())
    jobExecutionDao.saveJobExecution(jobExecution)

    and:
    jobExecution.startTime = new Date()
    jobExecution.endTime = new Date()
    jobExecution.status = BatchStatus.COMPLETED
    jobExecution.exitStatus = ExitStatus.COMPLETED
    jobExecution.createTime = new Date()
    jobExecution.lastUpdated = new Date()

    when:
    jobExecutionDao.updateJobExecution(jobExecution)

    then:
    with(jobExecutionDao.getJobExecution(jobExecution.id)) {
      startTime == jobExecution.startTime
      endTime == jobExecution.endTime
      status == jobExecution.status
      exitStatus == jobExecution.exitStatus
      createTime == jobExecution.createTime
      lastUpdated == jobExecution.lastUpdated
    }
  }

  def "updateJobExecution can persist a change to the related JobInstance"() {
    given:
    def jobExecution = new JobExecution(jobInstance, noParameters())
    jobExecutionDao.saveJobExecution(jobExecution)

    and:
    def newJobInstance = jobInstanceDao.createJobInstance("bar", noParameters())
    jobExecution.jobInstance = newJobInstance

    when:
    jobExecutionDao.updateJobExecution(jobExecution)

    then:
    with(jobExecutionDao.getJobExecution(jobExecution.id)) {
      jobId == newJobInstance.id
    }

    and:
    with(jobInstanceDao.getJobInstance(jobExecution)) {
      id == newJobInstance.id
    }
  }

  def "updateJobExecution increments the version"() {
    given:
    def jobExecution = new JobExecution(jobInstance, noParameters())

    and:
    jobExecutionDao.saveJobExecution(jobExecution)

    when:
    jobExecutionDao.updateJobExecution(jobExecution)

    then:
    with(jobExecutionDao.getJobExecution(jobExecution.id)) {
      version == old(jobExecution.version) + 1
    }
  }

  def "updateJobExecution rejects a JobExecution that has not already been saved"() {
    given:
    def jobExecution = new JobExecution(jobInstance, noParameters())

    when:
    jobExecutionDao.updateJobExecution(jobExecution)

    then:
    thrown IllegalArgumentException
  }

  def "updateJobExecution rejects an unsaved JobExecution with an assigned id"() {
    given:
    def jobExecution = new JobExecution(1L)

    when:
    jobExecutionDao.updateJobExecution(jobExecution)

    then:
    thrown IllegalArgumentException
  }

  def "findJobExecutions finds all related to a JobInstance"() {
    given:
    def jobInstance2 = jobInstanceDao.createJobInstance("bar", noParameters())

    and:
    3.times {
      jobExecutionDao.saveJobExecution(new JobExecution(jobInstance, noParameters()))
    }
    jobExecutionDao.saveJobExecution(new JobExecution(jobInstance2, noParameters()))

    when:
    def jobExecutions = jobExecutionDao.findJobExecutions(jobInstance)

    then:
    jobExecutions.size() == 3
    jobExecutions.jobId.every {
      it == jobInstance.id
    }
  }

  def "getLastJobExecution finds the last created execution for a job"() {
    given:
    def executions = (1..3).collect { i ->
      def jobExecution = new JobExecution(jobInstance, noParameters())
      jobExecution.createTime = new Date() - i
      return jobExecution
    }

    and:
    executions.each {
      jobExecutionDao.saveJobExecution(it)
    }

    when:
    def jobExecution = jobExecutionDao.getLastJobExecution(jobInstance)

    then:
    jobExecution.createTime == executions.createTime.max()
  }

  def "getLastJobExecution returns the correct result if an execution's timestamp is changed"() {
    given:
    def execution1 = new JobExecution(jobInstance, noParameters())
    execution1.createTime = new Date() - 2
    def execution2 = new JobExecution(jobInstance, noParameters())
    execution2.createTime = new Date() - 2

    and:
    [execution1, execution2].each {
      jobExecutionDao.saveJobExecution(it)
    }

    expect:
    jobExecutionDao.getLastJobExecution(jobInstance) == execution2

    when:
    execution1.createTime = new Date()
    jobExecutionDao.updateJobExecution(execution1)

    then:
    jobExecutionDao.getLastJobExecution(jobInstance) == execution1
  }
}
