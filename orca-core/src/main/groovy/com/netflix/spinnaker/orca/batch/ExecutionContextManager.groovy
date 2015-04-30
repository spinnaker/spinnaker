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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.scope.context.ChunkContext

@Slf4j
@Canonical
@CompileStatic
class ExecutionContextManager {
  private static final String GLOBAL_KEY_PREFIX = "global-"

  static <T extends Execution> Stage<T> retrieve(Stage<T> stage, ChunkContext chunkContext) {
    def jobExecutionContext = chunkContext.stepContext.jobExecutionContext
    jobExecutionContext.each { String key, Object globalValue ->
      if (key.startsWith(GLOBAL_KEY_PREFIX)) {
        key = key.replace(GLOBAL_KEY_PREFIX, "")
        if (!stage.context.containsKey(key)) {
          // global value should only apply if stage context does not already contain a local value
          stage.context.put(key, globalValue)
        }
      }
    }

    if(stage.context) {
      def augmentedContext = [:] + stage.context
      if (stage.execution instanceof Pipeline) {
        augmentedContext.put('trigger', ((Pipeline) stage.execution).trigger)
      }
      stage.context.putAll(ContextParameterProcessor.process(stage.context, augmentedContext))
    }

    return stage
  }

  static void store(ChunkContext chunkContext, TaskResult taskResult) {
    def jobExecutionContext = chunkContext.stepContext.stepExecution.jobExecution.executionContext
    taskResult.globalOutputs.each { String key, Serializable obj ->
      jobExecutionContext.put(globalId(key), obj)
    }
  }

  private static String globalId(String key) {
    return "${GLOBAL_KEY_PREFIX}${key}"
  }
}
