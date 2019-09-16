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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeploymentMonitor
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.MonitoredDeployStageData
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorService
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentStep
import com.netflix.spinnaker.orca.deploymentmonitor.models.EvaluateHealthResponse
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import spock.lang.Specification
import com.netflix.spinnaker.config.DeploymentMonitorServiceProvider

import java.time.Instant

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class EvaluateDeploymentHealthTaskSpec extends Specification {
  ObjectMapper mapper = new ObjectMapper()
  Execution pipe = pipeline {
  }

  def "should retry retrofit errors"() {
    given:
    def monitorServiceStub = Stub(DeploymentMonitorService) {
      evaluateHealth(_) >> {
        throw RetrofitError.networkError("url", new IOException())
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

    def task = new EvaluateDeploymentHealthTask(serviceProviderStub, new NoopRegistry())

    MonitoredDeployStageData stageData = new MonitoredDeployStageData()
    stageData.deploymentMonitor = new DeploymentMonitor()
    stageData.deploymentMonitor.id = "LogMonitorId"
    stageData.application = pipe.application

    def stage = new Stage(pipe, "evaluateDeploymentHealth", mapper.convertValue(stageData, Map))
    stage.startTime = Instant.now().toEpochMilli()

    when:
    TaskResult result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.deployMonitorHttpRetryCount == 1

    when:
    stage.context.deployMonitorHttpRetryCount = 3
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
  }

  def "should handle bad responses from 3rd party monitor"() {
    given:
    def monitorServiceStub = Stub(DeploymentMonitorService) {
//    notifyStarting(_) >> {
//    }
//
//    notifyCompleted(_) >> {
//    }
//
      evaluateHealth(_) >> {
        return new EvaluateHealthResponse()
      } >> {
        EvaluateHealthResponse response = new EvaluateHealthResponse()
        response.nextStep = new DeploymentStep()

        return response
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

    def task = new EvaluateDeploymentHealthTask(serviceProviderStub, new NoopRegistry())

    MonitoredDeployStageData stageData = new MonitoredDeployStageData()
    stageData.deploymentMonitor = new DeploymentMonitor()
    stageData.deploymentMonitor.id = "LogMonitorId"
    stageData.application = pipe.application

    def stage = new Stage(pipe, "evaluateDeploymentHealth", mapper.convertValue(stageData, Map))
    stage.startTime = Instant.now().toEpochMilli()

    when: 'monitor returns an empty EvaluateHealthResponse'
    TaskResult result = task.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL

    when: 'monitor returns an empty EvaluateHealthResponse.DeploymentStep'
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
  }
}
