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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionContextManagerSpec extends Specification {

  @Shared ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()

  @Unroll
  def "should not overwrite a local value with a global value"() {
    given:
    def trigger = [trigger: "Trigger Details"]
    def pipeline = new Pipeline()
    pipeline.trigger.putAll(trigger)
    def stage = new Stage<>(pipeline, null, context)
    def chunkContext = Stub(ChunkContext) {
      getStepContext() >> {
        return Stub(StepContext) {
          getJobExecutionContext() >> {
            return jobExecutionContext
          }
        }
      }
    }

    when:
    ExecutionContextManager.retrieve(stage, chunkContext, contextParameterProcessor)

    then:
    stage.context.execution == pipeline
    stage.context.trigger == trigger
    stage.context."doesNotExist" == null
    stage.context."key" == expectedValue

    where:
    context          | jobExecutionContext     || expectedValue
    ["key": "value"] | [:]                     || "value"
    ["key": "value"] | ["key": "global-value"] || "value"
    [:]              | ["key": "global-value"] || "global-value"
  }

  @Unroll
  def "should resolve expressions"() {
    given:
    def pipeline = new Pipeline()
    def stage = new Stage<>(pipeline, null, context)
    def chunkContext = Stub(ChunkContext) {
      getStepContext() >> {
        return Stub(StepContext) {
          getJobExecutionContext() >> {
            return [:]
          }
        }
      }
    }

    when:
    ExecutionContextManager.retrieve(stage, chunkContext, contextParameterProcessor)

    then:
    stage.context.key == expectedValue

    where:
    context                   || expectedValue
    [key: '${1 == 1}']        || true
    [key: '${1 == 2}']        || false
    [key: [key: '${1 == 1}']] || [key: true]
    [key: [key: '${1 == 2}']] || [key: false]
  }

  @Unroll
  def "should convert SPEL expressions into actual values"() {
    given:
    def stage = new Stage<>(new Pipeline(), null, ["key": "normal-string", "replaceKey": '${#alphanumerical(key)}'])
    def chunkContext = Mock(ChunkContext) {
      _ * getStepContext() >> {
        return Mock(StepContext) {
          1 * getJobExecutionContext() >> {
            return [:]
          }
        }
      }
    }

    when:
    ExecutionContextManager.retrieve(stage, chunkContext, contextParameterProcessor)
    def contextCopy = [:] + stage.context

    then:
    stage.context == ["key": "normal-string", "replaceKey": "normalstring"]
    contextCopy == ["key": "normal-string", "replaceKey": "normalstring"]
  }
}
