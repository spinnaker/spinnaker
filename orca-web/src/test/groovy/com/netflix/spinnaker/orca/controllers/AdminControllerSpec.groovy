/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.controllers

import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import de.huxhorn.sulky.ulid.ULID
import spock.lang.Specification
import spock.lang.Unroll

class AdminControllerSpec extends Specification {

  ExecutionRepository executionRepository = Mock(ExecutionRepository)

  AdminController controller = new AdminController(executionRepository: executionRepository)

  def setup() {
    executionRepository = Mock(ExecutionRepository)
    controller = new AdminController(executionRepository: executionRepository)
  }

  @Unroll
  def 'should throw error while saving execution with status: #invalidStatus '() {
      given:
      String executionId =  new ULID().nextULID()
      Execution execution = new Execution(Execution.ExecutionType.PIPELINE, executionId, 'testapp')

      when:
      execution.status = invalidStatus
      controller.createExecution(execution)

      then:
      thrown(InvalidRequestException)
      1 * executionRepository.retrieve(Execution.ExecutionType.PIPELINE, executionId) >> { throw new ExecutionNotFoundException('No execution')}
      0 * _

      where:
      invalidStatus << [ExecutionStatus.RUNNING, ExecutionStatus.PAUSED, ExecutionStatus.NOT_STARTED]
  }

  @Unroll
  def 'should succeed while saving execution with status: #validStatus '() {
    given:
    String executionId = new ULID().nextULID()
    Execution execution = new Execution(Execution.ExecutionType.PIPELINE, executionId, 'testapp')

    when:
    execution.status = validStatus
    Map result = controller.createExecution(execution)

    then:
    noExceptionThrown()
    1 * executionRepository.retrieve(Execution.ExecutionType.PIPELINE, executionId) >> { throw new ExecutionNotFoundException('No execution')}
    1 * executionRepository.store(execution)
    0 * _
    result.executionId == executionId
    result.status == validStatus

    where:
    validStatus << [ExecutionStatus.SUCCEEDED, ExecutionStatus.CANCELED, ExecutionStatus.TERMINAL]
  }

}
