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
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.repository.dao.JobExecutionDao
import org.springframework.batch.core.repository.dao.JobInstanceDao
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

abstract class JobExecutionDaoTck extends Specification {

  @Subject JobExecutionDao jobExecutionDao
  def JobInstanceDao jobInstanceDao

  protected static final JobParameters NO_PARAMETERS = new JobParameters()

  @Unroll("saveJobExecution stores field values correctly with #description")
  def "saveJobExecution stores field values correctly"() {
    given:
    def jobInstance = jobInstanceDao.createJobInstance("foo", NO_PARAMETERS)
    def jobExecution = new JobExecution(jobInstance, NO_PARAMETERS, fields.jobConfigurationName)

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
    def jobInstance = jobInstanceDao.createJobInstance("foo", NO_PARAMETERS)
    def jobExecution = new JobExecution(jobInstance, NO_PARAMETERS)

    when:
    jobExecutionDao.saveJobExecution(jobExecution)

    then:
    jobExecution.id != null
  }

  def "saveJobExecution assigns a version"() {
    given:
    def jobInstance = jobInstanceDao.createJobInstance("foo", NO_PARAMETERS)
    def jobExecution = new JobExecution(jobInstance, NO_PARAMETERS)

    when:
    jobExecutionDao.saveJobExecution(jobExecution)

    then:
    jobExecution.version == 0
  }

}
