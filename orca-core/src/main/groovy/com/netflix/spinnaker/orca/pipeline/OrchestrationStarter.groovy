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

import groovy.transform.CompileStatic
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.batch.OrchestrationInitializerTasklet.createTasklet
import static java.util.UUID.randomUUID

@Component
@CompileStatic
class OrchestrationStarter extends AbstractOrchestrationInitiator<List<Stage>> {

  @Override
  protected List<Stage> createSubject(Map<String, Object> config) {
    def stageCollectionReference = []
    for (context in ((List<Map<String, Object>>) config.stages)) {
      def type = context.remove("type").toString()

      if (context.providerType) {
        type += "_$context.providerType"
      }

      if (stages.containsKey(type)) {
        def stage = new Stage(type, context)
        stageCollectionReference << stage
      } else {
        throw new NoSuchStageException(type)
      }
    }
    return stageCollectionReference
  }

  @Override
  protected Job build(Map<String, Object> config, List<Stage> subject) {
    // this is less-than-ideal
    def jobBuilder = jobs.get("Orchestration:${randomUUID()}")
                         .flow(createTasklet(steps, subject)) as JobFlowBuilder
    subject.each { stage ->
      stages.get(stage.type).build(jobBuilder, stage)
    }

    jobBuilder.build().build()
  }
}
