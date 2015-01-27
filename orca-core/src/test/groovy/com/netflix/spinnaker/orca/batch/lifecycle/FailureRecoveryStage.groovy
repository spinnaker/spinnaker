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

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageBuilder
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.job.builder.JobFlowBuilder

@CompileStatic
class FailureRecoveryStage extends StageBuilder {

  Task startTask, recoveryTask, endTask

  FailureRecoveryStage() {
    super("failureRecovery")
  }

  @Override
  JobFlowBuilder build(JobFlowBuilder jobBuilder, Stage stage) {
    def step1 = buildStep(stage, "startStep", startTask)
    def step2 = buildStep(stage, "recovery", recoveryTask)
    def step3 = buildStep(stage, "end", endTask)
    (JobFlowBuilder) jobBuilder.next(step1)
                               .on(ExitStatus.FAILED.exitCode).to(step2).next(step3)
                               .from(step1).next(step3)
  }
}
