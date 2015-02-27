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
import com.netflix.spinnaker.orca.pipeline.model.Stage
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
    jobExecutionContext.each { String key, Object object ->
      if (key.startsWith(GLOBAL_KEY_PREFIX)) {
        stage.context.put(key.replace(GLOBAL_KEY_PREFIX, ""), object)
      }
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
