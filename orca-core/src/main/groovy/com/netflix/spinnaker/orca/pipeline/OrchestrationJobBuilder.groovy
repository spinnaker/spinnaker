/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.batch.ExecutionPropagationListener
import com.netflix.spinnaker.orca.batch.OrchestrationInitializerTasklet
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class OrchestrationJobBuilder extends ExecutionJobBuilder<Orchestration> {

  @Autowired
  ExecutionPropagationListener executionStatusPropagationListener

  @Override
  Job build(Orchestration orchestration) {
    String name = jobNameFor(orchestration)
    JobBuilder jobBuilder = jobs.get(name)
    jobBuilder = jobBuilder.listener(executionStatusPropagationListener)

    Tasklet t = new OrchestrationInitializerTasklet(orchestration)
    Step tasklet = t.createTasklet(steps)
    JobFlowBuilder jobFlowBuilder = jobBuilder.flow(tasklet)
    List<Stage<Orchestration>> orchestrationStages = []
    orchestrationStages.addAll(orchestration.stages)
    for (stage in orchestrationStages) {
      stages.get(stage.type).build(jobFlowBuilder, stage)
    }

    jobFlowBuilder.build().build()
  }

  @Override
  String jobNameFor(Orchestration orchestration) {
    "Orchestration:$orchestration.id"
  }

}
