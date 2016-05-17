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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.batch.core.scope.context.ChunkContext

@Slf4j
@Canonical
class ExecutionContextManager {
  static <T extends Execution> Stage<T> retrieve(Stage<T> stage, ChunkContext chunkContext) {
    ((AbstractStage)stage).context = new DelegatingHashMap(stage.context, chunkContext, stage)
    return stage
  }

  static void store(ChunkContext chunkContext, TaskResult taskResult) {
    def jobExecutionContext = chunkContext.stepContext.stepExecution.jobExecution.executionContext
    taskResult.globalOutputs.each { String key, Serializable obj ->
      jobExecutionContext.put(key, obj)
    }
  }

  static class DelegatingHashMap implements Map<String, Object> {
    @Delegate(excludes = "get")
    private final Map<String, Object> delegate

    private final ChunkContext chunkContext

    private final Stage stage

    DelegatingHashMap(Map<String, Object> delegate, ChunkContext chunkContext, Stage stage) {
      this.delegate = delegate
      this.chunkContext = chunkContext
      this.stage = stage
    }

    public Object get(Object key) {
      if (stage.execution instanceof Pipeline) {
        if (key == "trigger") {
          return ((Pipeline) stage.execution).trigger
        }

        if (key == "execution") {
          return stage.execution
        }
      }

      def jobExecutionContext = chunkContext.stepContext.jobExecutionContext

      def result = delegate.get(key)
      if (result == null) {
        result = jobExecutionContext.get(key)
      }

      if (result instanceof String && ContextParameterProcessor.containsExpression(result)) {
        def augmentedContext = [:] + jobExecutionContext + delegate
        if (stage.execution instanceof Pipeline) {
          augmentedContext.put('trigger', ((Pipeline) stage.execution).trigger)
          augmentedContext.put('execution', stage.execution)
        }
        def processed = ContextParameterProcessor.process([(key): result], augmentedContext, true)
        return processed[key]
      }

      return result
    }

    public Set<Map.Entry<Object, Object>> entrySet() {
      def jobExecutionContext = chunkContext.stepContext.jobExecutionContext
      def augmentedContext = [:] + jobExecutionContext + delegate
      if (stage.execution instanceof Pipeline) {
        augmentedContext.put('trigger', ((Pipeline) stage.execution).trigger)
        augmentedContext.put('execution', stage.execution)
      }
      def processed = ContextParameterProcessor.process(delegate, augmentedContext, true)
      return processed?.entrySet() ?: [:].entrySet()
    }
  }
}
