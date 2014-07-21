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

import com.google.common.base.Optional
import groovy.io.FileType
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.repository.dao.ExecutionContextDao
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import org.springframework.batch.core.repository.dao.StepExecutionDao
import org.springframework.batch.item.ExecutionContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.batch.BatchHelpers.noParameters

abstract class ExecutionContextDaoTck extends Specification {

  @Subject ExecutionContextDao executionContextDao
  JobInstanceDao jobInstanceDao
  JobExecutionDao jobExecutionDao
  StepExecutionDao stepExecutionDao

  JobInstance jobInstance
  JobExecution jobExecution
  StepExecution stepExecution

  def setup() {
    jobInstanceDao = createJobInstanceDao()
    jobExecutionDao = createJobExecutionDao(jobInstanceDao)
    stepExecutionDao = createStepExecutionDao()
    executionContextDao = createExecutionContextDao()

    jobInstance = jobInstanceDao.createJobInstance("foo", noParameters())
    jobExecution = new JobExecution(jobInstance, noParameters())
    jobExecutionDao.saveJobExecution(jobExecution)
    stepExecution = new StepExecution("step", jobExecution)
    stepExecutionDao.saveStepExecution(stepExecution)
  }

  abstract JobInstanceDao createJobInstanceDao()

  abstract JobExecutionDao createJobExecutionDao(JobInstanceDao jobInstanceDao)

  abstract StepExecutionDao createStepExecutionDao()

  abstract ExecutionContextDao createExecutionContextDao()

  def "can save and retrieve the execution context for a job"() {
    given:
    jobExecution.executionContext = new ExecutionContext(context)

    when:
    executionContextDao.saveExecutionContext(jobExecution)

    then:
    executionContextDao.getExecutionContext(jobExecution).entrySet() == context.entrySet()

    where:
    context = [a: "foo", b: "bar"]
  }

  def "can save and retrieve the execution context for a step"() {
    given:
    stepExecution.executionContext = new ExecutionContext(context)

    when:
    executionContextDao.saveExecutionContext(stepExecution)

    then:
    executionContextDao.getExecutionContext(stepExecution).entrySet() == context.entrySet()

    where:
    context = [a: "foo", b: "bar"]
  }

  def "updating a job execution context adds, updated and removes keys"() {
    given:
    jobExecution.executionContext = new ExecutionContext(a: "a", b: "b")
    executionContextDao.saveExecutionContext(jobExecution)

    when:
    jobExecution.executionContext.with {
      remove("b")
      put("c", "c")
      put("a", "A")
    }
    executionContextDao.updateExecutionContext(jobExecution)

    then:
    executionContextDao.getExecutionContext(jobExecution).entrySet() == [a: "A", c: "c"].entrySet()
  }

  def "updating a step execution context adds, updated and removes keys"() {
    given:
    stepExecution.executionContext = new ExecutionContext(a: "a", b: "b")
    executionContextDao.saveExecutionContext(stepExecution)

    when:
    stepExecution.executionContext.with {
      remove("b")
      put("c", "c")
      put("a", "A")
    }
    executionContextDao.updateExecutionContext(stepExecution)

    then:
    executionContextDao.getExecutionContext(stepExecution).entrySet() == [a: "A", c: "c"].entrySet()
  }

  @Unroll("can persist #type value")
  def "can persist different types of data"() {
    given:
    stepExecution.executionContext.put(key, value)
    executionContextDao.saveExecutionContext(stepExecution)

    expect:
    executionContextDao.getExecutionContext(stepExecution).key == value

    where:
    value                | type
    "foo"                | "a string"
    1                    | "an int"
    1L                   | "a long"
    1.1D                 | "a double"
    new Date()           | "a Date"
    Optional.absent()    | "a Serializable"
    null                 | "a null"
    FileType.DIRECTORIES | "an enum"

    key = "key"
  }

}
