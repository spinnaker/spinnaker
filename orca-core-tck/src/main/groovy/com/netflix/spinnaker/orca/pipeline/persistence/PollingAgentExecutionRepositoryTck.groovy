/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.pipeline.persistence

import com.netflix.spinnaker.orca.notifications.scheduling.PollingAgentExecutionRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.orchestration
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

@Subject(PollingAgentExecutionRepository)
@Unroll
abstract class PollingAgentExecutionRepositoryTck<T extends PollingAgentExecutionRepository> extends Specification {

  @Subject
  PollingAgentExecutionRepository repository

  @Subject
  PollingAgentExecutionRepository previousRepository

  void setup() {
    repository = createExecutionRepository()
    previousRepository = createExecutionRepositoryPrevious()
  }

  abstract T createExecutionRepository()

  abstract T createExecutionRepositoryPrevious()

  def "can retrieve all application names in database"() {
    given:
    def execution1 = pipeline {
      application = "spindemo"
    }
    def execution2 = pipeline {
      application = "orca"
    }
    def execution3 = orchestration {
      application = "spindemo"
    }
    def execution4 = orchestration {
      application = "spindemo"
    }

    when:
    repository.store(execution1)
    repository.store(execution2)
    repository.store(execution3)
    repository.store(execution4)
    def apps = repository.retrieveAllApplicationNames(executionType, minExecutions)

    then:
    apps.sort() == expectedApps.sort()

    where:
    executionType | minExecutions || expectedApps
    ORCHESTRATION | 0             || ["spindemo"]
    PIPELINE      | 0             || ["spindemo", "orca"]
    null          | 0             || ["spindemo", "orca"]
    null          | 2             || ["spindemo"]
    PIPELINE      | 2             || []
  }
}
