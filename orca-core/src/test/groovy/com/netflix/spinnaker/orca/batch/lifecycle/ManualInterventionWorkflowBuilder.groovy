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

package com.netflix.spinnaker.orca.batch.lifecycle

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.workflow.WorkflowBuilderSupport
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.job.builder.FlowJobBuilder
import org.springframework.batch.core.job.builder.JobBuilder

@CompileStatic
class ManualInterventionWorkflowBuilder extends WorkflowBuilderSupport<FlowJobBuilder> {

  Task preInterventionTask, postInterventionTask, finalTask

  @Override
  FlowJobBuilder build(JobBuilder jobBuilder) {
    def step1 = steps.get("PreInterventionStep")
        .tasklet(TaskTaskletAdapter.decorate(preInterventionTask))
        .build()
    def step2 = steps.get("PostInterventionStep")
        .tasklet(TaskTaskletAdapter.decorate(postInterventionTask))
        .build()
    def step3 = steps.get("FinalStep")
        .tasklet(TaskTaskletAdapter.decorate(finalTask))
        .build()
    jobBuilder.start(step1)
        .on(ExitStatus.STOPPED.exitCode).stopAndRestart(step2)
        .from(step1)
        .on(ExitStatus.COMPLETED.exitCode).to(step2)
        .next(step3)
        .build()
  }

  @Override
  FlowJobBuilder build(FlowJobBuilder jobBuilder) {
    throw new UnsupportedOperationException()
  }
}
