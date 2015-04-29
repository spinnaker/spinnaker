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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionContextManagerSpec extends Specification {
  @Unroll
  def "should not overwrite a local value with a global value"() {
    given:
    def stage = new PipelineStage(new Pipeline(), null, context)
    def chunkContext = Mock(ChunkContext) {
      1 * getStepContext() >> {
        return Mock(StepContext) {
          1 * getJobExecutionContext() >> {
            return jobExecutionContext
          }
        }
      }
    }

    when:
    ExecutionContextManager.retrieve(stage, chunkContext)

    then:
    stage.context == expectedContext

    where:
    context          | jobExecutionContext            || expectedContext
    [:]              | [:]                            || [:]
    [:]              | ["invalid": "invalid-value"]   || [:]
    ["key": "value"] | [:]                            || ["key": "value"]
    ["key": "value"] | ["global-key": "global-value"] || ["key": "value"]
    [:]              | ["global-key": "global-value"] || ["key": "global-value"]
  }

  @Unroll
  def "should convert SPEL expressions into actual values"() {
    given:
    def stage = new PipelineStage(new Pipeline(), null, [ "key": "normal-string", "replaceKey": '${#alphanumerical(key)}' ] )
    def chunkContext = Mock(ChunkContext) {
      1 * getStepContext() >> {
        return Mock(StepContext) {
          1 * getJobExecutionContext() >> {
            return [:]
          }
        }
      }
    }

    when:
    ExecutionContextManager.retrieve(stage, chunkContext)

    then:
    stage.context == ["key":"normal-string", "replaceKey": "normalstring"]

  }
}
