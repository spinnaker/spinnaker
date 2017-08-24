/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.model

import java.util.concurrent.atomic.AtomicInteger
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.ExecutionStatus
import groovy.transform.CompileStatic
import static com.netflix.spinnaker.orca.pipeline.model.Execution.DEFAULT_EXECUTION_ENGINE

@CompileStatic
class PipelineBuilder {

  private final Pipeline pipeline
  private final AtomicInteger nextRefid = new AtomicInteger(1)

  PipelineBuilder(String application, Registry registry) {
    this(application)
    pipeline.context = new AlertOnAccessMap<Pipeline>(pipeline, registry)
  }

  PipelineBuilder(String application) {
    pipeline = new Pipeline(application)
  }

  PipelineBuilder withTrigger(Map<String, Object> trigger = [:]) {
    pipeline.trigger.clear()
    if (trigger) {
      pipeline.trigger.putAll(trigger)
    }
    return this
  }

  PipelineBuilder withNotifications(List<Map<String, Object>> notifications = []) {
    pipeline.notifications.clear()
    if (notifications) {
      pipeline.notifications.addAll(notifications)
    }
    return this
  }

  PipelineBuilder withPipelineConfigId(String id) {
    pipeline.pipelineConfigId = id
    return this
  }

  PipelineBuilder withStage(String type, String name = type, Map<String, Object> context = [:]) {
    if (context.providerType && !(context.providerType in ['aws', 'titus'])) {
      type += "_$context.providerType"
    }

    pipeline.stages << new Stage<>(pipeline, type, name, context)
    return this
  }

  PipelineBuilder withStages(List<Map<String, Object>> stages) {
    stages.each {
      def type = it.remove("type").toString()
      def name = it.remove("name").toString()
      withStage(type, name ?: type, it)
    }
    return this
  }

  PipelineBuilder withStages(String... stageTypes) {
    withStages stageTypes.collect { String it ->
      def refId = nextRefid.getAndIncrement()
      [
        type                : it,
        refId               : refId.toString(),
        requisiteStageRefIds: refId == 1 ? [] : [(refId - 1).toString()]
      ] as Map<String, Object>
    }
    return this
  }

  Pipeline build() {
    pipeline.buildTime = System.currentTimeMillis()
    pipeline.authentication = Execution.AuthenticationDetails.build().orElse(new Execution.AuthenticationDetails())

    pipeline
  }

  PipelineBuilder withApplication(String application) {
    pipeline.application = application
    return this
  }

  PipelineBuilder withName(String name) {
    pipeline.name = name
    return this
  }

  PipelineBuilder withParallel(boolean parallel) {
    pipeline.parallel = parallel
    return this
  }

  PipelineBuilder withLimitConcurrent(boolean concurrent) {
    pipeline.limitConcurrent = concurrent
    return this
  }

  PipelineBuilder withKeepWaitingPipelines(boolean waiting) {
    pipeline.keepWaitingPipelines = waiting
    return this
  }

  PipelineBuilder withId(id = UUID.randomUUID().toString()) {
    pipeline.id = id
    return this
  }

  PipelineBuilder withGlobalContext(Map<String, Object> context) {
    pipeline.context.clear()
    pipeline.context.putAll(context)
    return this
  }

  PipelineBuilder withStatus(ExecutionStatus executionStatus) {
    pipeline.status = executionStatus
    return this
  }

  PipelineBuilder withStartTime(long startTime) {
    pipeline.startTime = startTime
    return this
  }

  PipelineBuilder withExecutionEngine(Execution.ExecutionEngine executionEngine) {
    pipeline.executionEngine = executionEngine ?: DEFAULT_EXECUTION_ENGINE
    return this
  }

  PipelineBuilder withOrigin(String origin) {
    pipeline.origin = origin
    return this
  }
}
