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

package com.netflix.spinnaker.orca.clouddriver.tasks.monitoreddeploy

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.DeploymentMonitorDefinition
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorServiceProvider
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.monitoreddeploy.NotifyDeployCompletedStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.MonitoredDeployStageData
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorService
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentCompletedRequest
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentMonitorStageConfig
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit2.mock.Calls
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class NotifyDeployCompletedTaskSpec extends Specification {
  ObjectMapper mapper = new ObjectMapper()
  PipelineExecutionImpl pipe = pipeline {
  }

  def "should indicate deployment success or failure when no rollback is performed"() {
    given:
    def monitorServiceStub = Stub(DeploymentMonitorService) {
      notifyCompleted(_) >> {
        return Calls.response(null)
      }
    }

    def serviceProviderStub = Stub(DeploymentMonitorServiceProvider) {
      getDefinitionById(_) >> {
        def deploymentMonitor = new DeploymentMonitorDefinition()
        deploymentMonitor.id = "LogMonitorId"
        deploymentMonitor.name = "LogMonitor"
        deploymentMonitor.failOnError = true
        deploymentMonitor.service = monitorServiceStub

        return deploymentMonitor
      }
    }

    def task = new NotifyDeployCompletedTask(serviceProviderStub, new NoopRegistry())

    MonitoredDeployStageData stageData = new MonitoredDeployStageData()
    stageData.deploymentMonitor = new DeploymentMonitorStageConfig()
    stageData.deploymentMonitor.id = "LogMonitorId"
    stageData.application = pipe.application

    def stage = new StageExecutionImpl(pipe, NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE, mapper.convertValue(stageData, Map))
    stage.context.put("hasDeploymentFailed", false)

    when:
    TaskResult result = task.execute(stage)

    then:
    monitorServiceStub.notifyCompleted({
      it != null
    } as DeploymentCompletedRequest) >> { DeploymentCompletedRequest request ->
      assert request.status == DeploymentCompletedRequest.DeploymentStatus.SUCCESS &&
        request.rollback == DeploymentCompletedRequest.DeploymentStatus.ROLLBACK_NOT_PERFORMED
      return Calls.response(null)
    }


    when: 'deployment has failed'

    stage.context.put("hasDeploymentFailed", true)
    result = task.execute(stage)

    then:
    monitorServiceStub.notifyCompleted({
      it != null
    } as DeploymentCompletedRequest) >> { DeploymentCompletedRequest request ->
      assert request.status == DeploymentCompletedRequest.DeploymentStatus.FAILURE &&
        request.rollback == DeploymentCompletedRequest.DeploymentStatus.ROLLBACK_NOT_PERFORMED
      return Calls.response(null)
    }

    result.status == ExecutionStatus.SUCCEEDED
  }

  def "rollback stage was initiated"() {
    given:
    def monitorServiceStub = Stub(DeploymentMonitorService) {

      notifyCompleted(_) >> {
        return Calls.response(null)
      }
    }

    def serviceProviderStub = Stub(DeploymentMonitorServiceProvider) {
      getDefinitionById(_) >> {
        def deploymentMonitor = new DeploymentMonitorDefinition()
        deploymentMonitor.id = "LogMonitorId"
        deploymentMonitor.name = "LogMonitor"
        deploymentMonitor.failOnError = true
        deploymentMonitor.service = monitorServiceStub

        return deploymentMonitor
      }
    }

    def task = new NotifyDeployCompletedTask(serviceProviderStub, new NoopRegistry())

    MonitoredDeployStageData stageData = new MonitoredDeployStageData()
    stageData.deploymentMonitor = new DeploymentMonitorStageConfig()
    stageData.deploymentMonitor.id = "LogMonitorId"
    stageData.application = pipe.application


    def notifyCompleteStage = new StageExecutionImpl(pipe, NotifyDeployCompletedStage.PIPELINE_CONFIG_TYPE, mapper.convertValue(stageData, Map))
    def rollbackStage = new StageExecutionImpl(pipe, RollbackClusterStage.PIPELINE_CONFIG_TYPE, mapper.convertValue(stageData, Map))
    def createServerGroupStage = new StageExecutionImpl(pipe, CreateServerGroupStage.PIPELINE_CONFIG_TYPE, mapper.convertValue(stageData, Map))
    rollbackStage.status = ExecutionStatus.SUCCEEDED

    notifyCompleteStage.setParentStageId(createServerGroupStage.getId())
    rollbackStage.setParentStageId(createServerGroupStage.getId())
    pipe.stages.addAll([notifyCompleteStage, rollbackStage, createServerGroupStage])


    when: 'rollback was initiated and successful'
    TaskResult result = task.execute(notifyCompleteStage)

    then:

    monitorServiceStub.notifyCompleted({
      it != null
    } as DeploymentCompletedRequest) >> { DeploymentCompletedRequest request ->
      assert request.rollback == DeploymentCompletedRequest.DeploymentStatus.SUCCESS
      return Calls.response(null)
    }

    when: 'rollback was initiated and failed'

    rollbackStage.status = ExecutionStatus.FAILED_CONTINUE
    result = task.execute(notifyCompleteStage)

    then:

    monitorServiceStub.notifyCompleted({
      it != null
    } as DeploymentCompletedRequest) >> { DeploymentCompletedRequest request ->
      assert request.rollback == DeploymentCompletedRequest.DeploymentStatus.FAILURE
      return Calls.response(null)
    }

    result.status == ExecutionStatus.SUCCEEDED
  }
}


