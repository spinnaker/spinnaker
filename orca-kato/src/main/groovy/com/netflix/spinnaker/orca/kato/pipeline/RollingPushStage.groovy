/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.kato.tasks.DisableInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.TerminateInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForTerminatedInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.CheckForRemainingTerminationsTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.DetermineTerminationCandidatesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.DetermineTerminationPhaseInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.WaitForNewInstanceLaunchTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.sock.SockService
import com.netflix.spinnaker.orca.sock.tasks.GetCommitsTask
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RollingPushStage extends StageBuilder {
  static final String MAYO_CONFIG_TYPE = "rollingPush"

  @Autowired(required = false)
  SockService sockService

  RollingPushStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
    if (sockService) {
      jobBuilder.next(buildStep(stage, "getCommits", GetCommitsTask))
    }
    jobBuilder.next(buildStep(stage, "determineTerminationCandidates", DetermineTerminationCandidatesTask))
    def startOfCycle = buildStep(stage, "determineCurrentPhaseTerminations", DetermineTerminationPhaseInstancesTask)
    jobBuilder.next(startOfCycle)
    jobBuilder.next(buildStep(stage, "disableInstances", DisableInstancesTask))
    jobBuilder.next(buildStep(stage, "monitorDisable", MonitorKatoTask))
    jobBuilder.next(buildStep(stage, "waitForDisabledState", WaitForDownInstanceHealthTask))
    jobBuilder.next(buildStep(stage, "terminateInstances", TerminateInstancesTask))
    jobBuilder.next(buildStep(stage, "waitForTerminatedInstances", WaitForTerminatedInstancesTask))
    jobBuilder.next(buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask))
    jobBuilder.next(buildStep(stage, "waitForNewInstances", WaitForNewInstanceLaunchTask))
    jobBuilder.next(buildStep(stage, "waitForUpInstances", WaitForUpInstanceHealthTask))
    def endOfCycle = buildStep(stage, "checkForRemainingTerminations", CheckForRemainingTerminationsTask)
    jobBuilder.next(endOfCycle)
    jobBuilder.on(ExecutionStatus.REDIRECT.name()).to(startOfCycle)
    jobBuilder.from(endOfCycle).on('**').to(buildStep(stage, "pushComplete", pushComplete()))
  }

  Task pushComplete() {
    return new Task() {
      @Override
      TaskResult execute(Stage stage) {
        LoggerFactory.getLogger(RollingPushStage).info("Rolling Push completed for $stage.context.asgName in $stage.context.account / $stage.context.region")
        return DefaultTaskResult.SUCCEEDED
      }
    }
  }
}
