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

import org.springframework.batch.core.*
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.batch.core.repository.dao.StepExecutionDao
import org.springframework.dao.OptimisticLockingFailureException
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.CopyUtils.copy
import static com.netflix.spinnaker.orca.batch.BatchHelpers.noParameters

abstract class StepExecutionDaoTck extends Specification {

  @Subject StepExecutionDao stepExecutionDao
  JobInstanceDao jobInstanceDao
  JobExecutionDao jobExecutionDao

  JobInstance jobInstance
  JobExecution jobExecution

  def setup() {
    jobInstanceDao = createJobInstanceDao()
    jobExecutionDao = createJobExecutionDao(jobInstanceDao)
    stepExecutionDao = createStepExecutionDao()

    jobInstance = jobInstanceDao.createJobInstance("foo", noParameters())
    jobExecution = new JobExecution(jobInstance, noParameters())
    jobExecutionDao.saveJobExecution(jobExecution)
  }

  abstract JobInstanceDao createJobInstanceDao()

  abstract JobExecutionDao createJobExecutionDao(JobInstanceDao jobInstanceDao)

  abstract StepExecutionDao createStepExecutionDao()

  def "saveStepExecution persists all fields correctly"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution)

    when:
    stepExecutionDao.saveStepExecution(stepExecution)

    then:
    with(stepExecutionDao.getStepExecution(jobExecution, stepExecution.id)) {
      stepName == stepExecution.stepName
      status == stepExecution.status
      filterCount == stepExecution.filterCount
      readCount == stepExecution.readCount
      writeCount == stepExecution.writeCount
      commitCount == stepExecution.commitCount
      rollbackCount == stepExecution.rollbackCount
      readSkipCount == stepExecution.readSkipCount
      processSkipCount == stepExecution.processSkipCount
      writeSkipCount == stepExecution.writeSkipCount
      startTime == stepExecution.startTime
      endTime == stepExecution.endTime
      lastUpdated == stepExecution.lastUpdated
      exitStatus == stepExecution.exitStatus
      terminateOnly == stepExecution.terminateOnly
    }
  }

  // TODO: test persistence of execution context

  def "saveStepExecution assigns an id"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution)

    expect:
    stepExecution.id == null

    when:
    stepExecutionDao.saveStepExecution(stepExecution)

    then:
    stepExecution.id != null
  }

  def "saveStepExecution assigns a version"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution)

    expect:
    stepExecution.version == null

    when:
    stepExecutionDao.saveStepExecution(stepExecution)

    then:
    stepExecution.version == 0
  }

  def "saveStepExecution rejects a StepExecution that has already been saved"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution)

    and:
    stepExecutionDao.saveStepExecution(stepExecution)

    when:
    stepExecutionDao.saveStepExecution(stepExecution)

    then:
    thrown IllegalArgumentException
  }

  def "saveJobExecution rejects a StepExecution if its JobExecution is not saved"() {
    given:
    def stepExecution = new StepExecution("foo", new JobExecution(jobInstance, noParameters()))

    when:
    stepExecutionDao.saveStepExecution(stepExecution)

    then:
    thrown IllegalArgumentException
  }

  def "saveJobExecutions saves each JobExecution in a collection"() {
    given:
    def executions = (1..3).collect {
      new StepExecution("foo", jobExecution)
    }

    when:
    stepExecutionDao.saveStepExecutions(executions)

    then:
    executions.id.every {
      it != null
    }
  }

  def "updateStepExecution rejects an unsaved StepExecution"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution)

    when:
    stepExecutionDao.updateStepExecution(stepExecution)

    then:
    thrown IllegalArgumentException
  }

  def "updateStepExecution rejects a StepExecution with an assigned id that was never saved"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution, 1)

    when:
    stepExecutionDao.updateStepExecution(stepExecution)

    then:
    thrown IllegalArgumentException
  }

  def "updateStepExecution increments the version"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution)
    stepExecutionDao.saveStepExecution(stepExecution)

    when:
    stepExecutionDao.updateStepExecution(stepExecution)

    then:
    stepExecution.version > old(stepExecution.version)
  }

  def "updateStepExecution rejects a stale StepExecution"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution)
    stepExecutionDao.saveStepExecution(stepExecution)

    and:
    def staleStepExecution = copy(stepExecution)
    stepExecutionDao.updateStepExecution(stepExecution)

    expect:
    stepExecution.version > staleStepExecution.version

    when:
    stepExecutionDao.updateStepExecution(staleStepExecution)

    then:
    thrown OptimisticLockingFailureException
  }

  def "updateStepExecution persists all fields correctly"() {
    given:
    def stepExecution = new StepExecution("foo", jobExecution)
    stepExecutionDao.saveStepExecution(stepExecution)

    and:
    stepExecution.status = BatchStatus.COMPLETED
    stepExecution.filterCount = 1
    stepExecution.readCount = 1
    stepExecution.writeCount = 1
    stepExecution.commitCount = 1
    stepExecution.rollbackCount = 1
    stepExecution.readSkipCount = 1
    stepExecution.processSkipCount = 1
    stepExecution.writeSkipCount = 1
    stepExecution.endTime = new Date()
    stepExecution.lastUpdated = new Date()
    stepExecution.exitStatus = ExitStatus.COMPLETED
    stepExecution.setTerminateOnly()

    when:
    stepExecutionDao.updateStepExecution(stepExecution)

    then:
    with(stepExecutionDao.getStepExecution(jobExecution, stepExecution.id)) {
      stepName == stepExecution.stepName
      status == stepExecution.status
      filterCount == stepExecution.filterCount
      readCount == stepExecution.readCount
      writeCount == stepExecution.writeCount
      commitCount == stepExecution.commitCount
      rollbackCount == stepExecution.rollbackCount
      readSkipCount == stepExecution.readSkipCount
      processSkipCount == stepExecution.processSkipCount
      writeSkipCount == stepExecution.writeSkipCount
      startTime == stepExecution.startTime
      endTime == stepExecution.endTime
      lastUpdated == stepExecution.lastUpdated
      exitStatus == stepExecution.exitStatus
      terminateOnly == stepExecution.terminateOnly
    }
  }

  def "addStepExecutions finds step executions belonging to a job execution"() {
    given:
    stepNames.each {
      stepExecutionDao.saveStepExecution(new StepExecution(it, jobExecution))
    }

    and:
    def differentJobExecution = new JobExecution(jobInstance, noParameters())
    jobExecutionDao.saveJobExecution(differentJobExecution)
    stepNames.each {
      stepExecutionDao.saveStepExecution(new StepExecution(it, differentJobExecution))
    }

    when:
    stepExecutionDao.addStepExecutions(jobExecution)

    then:
    with(jobExecution.stepExecutions) {
      size() == stepNames.size()
      stepName == stepNames.reverse()
    }

    where:
    stepNames = ["foo", "bar", "baz"]
  }
}
