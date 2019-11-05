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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.DeploymentMonitorDefinition
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorService
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentMonitorStageConfig
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentStep
import com.netflix.spinnaker.orca.deploymentmonitor.models.EvaluateHealthResponse
import com.netflix.spinnaker.orca.deploymentmonitor.models.MonitoredDeployInternalStageData
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import spock.lang.Specification
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorServiceProvider
import spock.lang.Unroll

import java.time.Instant
import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class EvaluateDeploymentHealthTaskSpec extends Specification {
  Execution pipe = pipeline {
  }

  def "should retry retrofit errors"() {
    given:
    def monitorServiceStub = Stub(DeploymentMonitorService) {
      evaluateHealth(_) >> {
        throw RetrofitError.networkError("url", new IOException())
      }
    }

    def serviceProviderStub = getServiceProviderStub(monitorServiceStub)

    def task = new EvaluateDeploymentHealthTask(serviceProviderStub, new NoopRegistry())

    MonitoredDeployInternalStageData stageData = new MonitoredDeployInternalStageData()
    stageData.deploymentMonitor = new DeploymentMonitorStageConfig()
    stageData.deploymentMonitor.id = "LogMonitorId"

    def stage = new Stage(pipe, "evaluateDeploymentHealth", stageData.toContextMap() + [application: pipe.application])
    stage.startTime = Instant.now().toEpochMilli()

    when: 'we can still retry'
    TaskResult result = task.execute(stage)

    then: 'should retry'
    result.status == ExecutionStatus.RUNNING
    result.context.deployMonitorHttpRetryCount == 1

    when: 'we ran out of retries'
    stage.context.deployMonitorHttpRetryCount = MonitoredDeployBaseTask.MAX_RETRY_COUNT
    result = task.execute(stage)

    then: 'should terminate'
    result.status == ExecutionStatus.TERMINAL

    when: 'we ran out of retries and failOnError = false'
    serviceProviderStub = getServiceProviderStub(monitorServiceStub, {DeploymentMonitorDefinition dm -> dm.failOnError = false})
    task = new EvaluateDeploymentHealthTask(serviceProviderStub, new NoopRegistry())
    result = task.execute(stage)

    then: 'should return fail_continue'
    result.status == ExecutionStatus.FAILED_CONTINUE

    when: 'we ran out of retries and failOnError = false but there is a stage override for failOnError=true'
    stageData.deploymentMonitor.failOnErrorOverride = true
    stage = new Stage(pipe, "evaluateDeploymentHealth", stageData.toContextMap() + [
      application: pipe.application,
      deployMonitorHttpRetryCount: MonitoredDeployBaseTask.MAX_RETRY_COUNT
    ])
    stage.startTime = Instant.now().toEpochMilli()
    result = task.execute(stage)

    then: 'should terminate'
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

    def serviceProviderStub = getServiceProviderStub(monitorServiceStub)

    def task = new EvaluateDeploymentHealthTask(serviceProviderStub, new NoopRegistry())

    MonitoredDeployInternalStageData stageData = new MonitoredDeployInternalStageData()
    stageData.deploymentMonitor = new DeploymentMonitorStageConfig()
    stageData.deploymentMonitor.id = "LogMonitorId"

    def stage = new Stage(pipe, "evaluateDeploymentHealth", stageData.toContextMap() + [application: pipe.application])
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

  def 'should compute task timeout'() {
    given:
    def monitorServiceStub = Mock(DeploymentMonitorService)

    def serviceProviderStub = getServiceProviderStub(monitorServiceStub,
      {DeploymentMonitorDefinition deploymentMonitor -> deploymentMonitor.maxAnalysisMinutes = 15})

    def task = new EvaluateDeploymentHealthTask(serviceProviderStub, new NoopRegistry())

    MonitoredDeployInternalStageData stageData = new MonitoredDeployInternalStageData()
    stageData.deploymentMonitor = new DeploymentMonitorStageConfig()
    stageData.deploymentMonitor.id = "LogMonitorId"

    when: 'no override is provided'
    def stage = new Stage(pipe, "evaluateDeploymentHealth", stageData.toContextMap() + [application: pipe.application])

    then:
    task.getTimeout() == 0
    task.getDynamicTimeout(stage) == TimeUnit.MINUTES.toMillis(15)

    when: 'stage override is provided'
    stageData.deploymentMonitor.maxAnalysisMinutesOverride = new Integer(21)
    stage = new Stage(pipe, "evaluateDeploymentHealth", stageData.toContextMap() + [application: pipe.application])

    then:
    task.getDynamicTimeout(stage) == TimeUnit.MINUTES.toMillis(21)
  }

  @Unroll
  def 'should respect failOnError during onTimeout'() {
    given:
    def monitorServiceStub = Mock(DeploymentMonitorService)

    def serviceProviderStub = getServiceProviderStub(monitorServiceStub,
      {DeploymentMonitorDefinition deploymentMonitor -> deploymentMonitor.failOnError = monitorFailOnError})

    def task = new EvaluateDeploymentHealthTask(serviceProviderStub, new NoopRegistry())

    MonitoredDeployInternalStageData stageData = new MonitoredDeployInternalStageData()
    stageData.deploymentMonitor = new DeploymentMonitorStageConfig()
    stageData.deploymentMonitor.id = "LogMonitorId"
    stageData.deploymentMonitor.failOnErrorOverride = stageFailOnError
    def stage = new Stage(pipe, "evaluateDeploymentHealth", stageData.toContextMap() + [application: pipe.application])

    when:
    def result = task.onTimeout(stage)

    then:
    result.status == expected

    where:
    monitorFailOnError | stageFailOnError || expected
    true               | true             || ExecutionStatus.TERMINAL
    true               | false            || ExecutionStatus.FAILED_CONTINUE
    true               | null             || ExecutionStatus.TERMINAL
    false              | true             || ExecutionStatus.TERMINAL
    false              | false            || ExecutionStatus.FAILED_CONTINUE
    false              | null             || ExecutionStatus.FAILED_CONTINUE
  }

  private getServiceProviderStub(monitorServiceStub) {
    return getServiceProviderStub(monitorServiceStub, {})
  }

  private getServiceProviderStub(monitorServiceStub, extraInitLambda) {
    return Stub(DeploymentMonitorServiceProvider) {
      getDefinitionById(_) >> {
        def deploymentMonitor = new DeploymentMonitorDefinition()
        deploymentMonitor.id = "LogMonitorId"
        deploymentMonitor.name = "LogMonitor"
        deploymentMonitor.failOnError = true
        deploymentMonitor.service = monitorServiceStub

        extraInitLambda(deploymentMonitor)

        return deploymentMonitor
      }
    }
  }
}
