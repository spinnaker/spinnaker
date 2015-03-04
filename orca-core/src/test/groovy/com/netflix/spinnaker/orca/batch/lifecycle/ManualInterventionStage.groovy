/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.lifecycle

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.job.builder.FlowBuilder

@CompileStatic
class ManualInterventionStage extends StageBuilder {

  Task preInterventionTask, postInterventionTask, finalTask

  ManualInterventionStage() {
    super("manualIntervention")
  }

  @Override
  FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
    def step1 = buildStep(stage, "preIntervention", preInterventionTask)
    def step2 = buildStep(stage, "postIntervention", postInterventionTask)
    def step3 = buildStep(stage, "final", finalTask)
    jobBuilder.next(step1)
      .on(ExitStatus.STOPPED.exitCode).stopAndRestart(step2)
      .from(step1)
      .on(ExitStatus.COMPLETED.exitCode).to(step2)
      .next(step3)
  }
}
