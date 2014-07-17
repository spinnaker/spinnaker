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
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.batch.core.repository.dao.StepExecutionDao
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.batch.repository.dao.BatchHelpers.noParameters

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

  def "saveStepExecution increments version"() {
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

}
