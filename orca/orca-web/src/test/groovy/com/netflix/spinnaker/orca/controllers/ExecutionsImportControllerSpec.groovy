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

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.model.ExecutionImportResponse
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import de.huxhorn.sulky.ulid.ULID
import retrofit.RetrofitError
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionsImportControllerSpec extends Specification {

  ExecutionRepository executionRepository = Mock(ExecutionRepository)

  Front50Service front50Service = Mock(Front50Service)

  ExecutionsImportController controller

  def setup() {
    executionRepository = Mock(ExecutionRepository)
    controller = new ExecutionsImportController(executionRepository, front50Service)
  }

  @Unroll
  def 'should throw error while saving execution with status: #invalidStatus '() {
    given:
    String executionId =  new ULID().nextULID()
    PipelineExecutionImpl execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, executionId, 'testapp')

    when:
    execution.status = invalidStatus
    controller.createExecution(execution)

    then:
    thrown(InvalidRequestException)
    1 * front50Service.get('testapp') >> { new Application(name: 'testapp') }
    1 * executionRepository.retrieve(ExecutionType.PIPELINE, executionId) >> { throw new ExecutionNotFoundException('No execution')}
    0 * _

    where:
    invalidStatus << [ExecutionStatus.RUNNING, ExecutionStatus.PAUSED, ExecutionStatus.NOT_STARTED]
  }

  @Unroll
  def 'should succeed while saving execution with status: #validStatus '() {
    given:
    String executionId = new ULID().nextULID()
    PipelineExecutionImpl execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, executionId, 'testapp')

    when:
    execution.status = validStatus
    ExecutionImportResponse result = controller.createExecution(execution)

    then:
    noExceptionThrown()
    1 * front50Service.get('testapp') >> { new Application(name: 'testapp') }
    1 * executionRepository.retrieve(ExecutionType.PIPELINE, executionId) >> { throw new ExecutionNotFoundException('No execution')}
    1 * executionRepository.store(execution)
    0 * _
    result.executionId == executionId
    result.executionStatus == validStatus

    where:
    validStatus << [ExecutionStatus.SUCCEEDED, ExecutionStatus.CANCELED, ExecutionStatus.TERMINAL]
  }

  def 'should succeed to import if unable to retrieve app from front50'() {
    given:
    String executionId = new ULID().nextULID()
    PipelineExecutionImpl execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, executionId, 'testapp')
    execution.status = ExecutionStatus.SUCCEEDED

    when:
    ExecutionImportResponse result = controller.createExecution(execution)

    then:
    noExceptionThrown()
    1 * front50Service.get('testapp') >> { throw new SpinnakerServerException(RetrofitError.unexpectedError('http://test.front50.com', new RuntimeException()))}
    1 * executionRepository.retrieve(ExecutionType.PIPELINE, executionId) >> { throw new ExecutionNotFoundException('No execution')}
    1 * executionRepository.store(execution)
    0 * _
    result.executionId == executionId
    result.executionStatus == ExecutionStatus.SUCCEEDED
  }

}
