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
import rx.subjects.ReplaySubject
import static com.netflix.spinnaker.orca.batch.OrchestrationInitializerTasklet.createTasklet
import static com.netflix.spinnaker.orca.batch.PipelineFulfillerTasklet.initializeFulfiller
import static java.util.UUID.randomUUID

@Component
@CompileStatic
class OrchestrationStarter extends AbstractOrchestrationInitiator<String> {

  protected Job build(Map<String, Object> config, ReplaySubject subject) {
    // this is less-than-ideal
    def stageCollectionReference = []
    def jobBuilder = jobs.get("orca-orchestration-${randomUUID()}")
      .flow(createTasklet(steps, stageCollectionReference, subject))
      .next(initializeFulfiller(steps, null, subject)) as JobFlowBuilder

    for (Map<String, Serializable> context in ((List<Map<String, Serializable>>) config.stages)) {
      def type = context.remove("type").toString()

      if (context.providerType) {
        type += "_$context.providerType"
      }

      if (stages.containsKey(type)) {
        def stage = new PipelineStage(type, context)
        stages.get(type).build(jobBuilder, stage)
        stageCollectionReference << stage
      } else {
        throw new NoSuchStageException(type)
      }
    }

    jobBuilder.build().build()
  }
}
