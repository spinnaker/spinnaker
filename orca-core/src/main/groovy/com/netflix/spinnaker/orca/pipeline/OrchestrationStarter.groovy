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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.batch.OrchestrationInitializerTasklet.createTasklet
import static java.util.UUID.randomUUID

@Component
//@CompileStatic
class OrchestrationStarter extends AbstractOrchestrationInitiator<Orchestration> {

  @Autowired
  ExecutionRepository executionRepository

  OrchestrationStarter() {
    super("orchestration")
  }

  @Override
  protected Orchestration create(Map<String, Object> config) {
    def orchestration = new Orchestration()
    if (config.containsKey("application")) {
      orchestration.application = config.application
    }
    if (config.containsKey("description")) {
      orchestration.description = config.description
    }
    if (config.containsKey("name")) {
      orchestration.description = config.name
    }

    for (context in ((List<Map<String, Object>>) config.stages)) {
      def type = context.remove("type").toString()

      if (context.providerType) {
        type += "_$context.providerType"
      }

      if (stages.containsKey(type)) {
        def stage = new OrchestrationStage(orchestration, type, context)
        orchestration.stages << stage
      } else {
        throw new NoSuchStageException(type)
      }
    }

    return orchestration
  }

  @Override
  protected Job build(Map<String, Object> config, Orchestration orchestration) {
    def jobBuilder = jobs.get("Orchestration:${randomUUID()}")
    pipelineListeners.each {
      jobBuilder = jobBuilder.listener(it)
    }
    jobBuilder = jobBuilder.flow(createTasklet(steps, orchestration)) as JobFlowBuilder
    orchestration.stages.each { stage ->
      stages.get(stage.type).build(jobBuilder, stage)
    }

    jobBuilder.build().build()
  }

  @Override
  protected void persistExecution(Orchestration orchestration) {
    executionRepository.store(orchestration)
  }
}
