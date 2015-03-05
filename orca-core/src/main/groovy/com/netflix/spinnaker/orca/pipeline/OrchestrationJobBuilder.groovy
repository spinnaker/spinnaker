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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.batch.OrchestrationInitializerTasklet.createTasklet
import static java.util.UUID.randomUUID

@Component
@CompileStatic
class OrchestrationJobBuilder extends ExecutionJobBuilder<Orchestration> {

  @Override
  @CompileDynamic
  Job build(Orchestration orchestration) {
    def jobBuilder = jobs.get(jobNameFor(orchestration))
    jobBuilder = jobBuilder.flow(createTasklet(steps, orchestration)) as JobFlowBuilder
    List<Stage<Orchestration>> orchestrationStages = []
    orchestrationStages.addAll(orchestration.stages)
    orchestrationStages.each { stage ->
      stages.get(stage.type).build(jobBuilder, stage)
    }

    jobBuilder.build().build()
  }

  @Override
  String jobNameFor(Orchestration orchestration) {
    "Orchestration:${randomUUID()}" // TODO: base on orchestration id
  }

}
