/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.pipeline

import com.netflix.spinnaker.orca.clouddriver.model.OperationLifecycle
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ClouddriverOperationStageSpec extends Specification {

  @Subject
  CloudOperationStage subject = new NoopClouddriverOperationStage()

  def "should support an operation before the primary"() {
    given:
    def target = stage {
      context = [
        "type" : "something",
        "cloudProvider": "mock"
      ]
    }

    when:
    def beforeGraph = StageGraphBuilderImpl.beforeStages(target)
    subject.beforeStages(target, beforeGraph)

    then:
    beforeGraph.build()*.name == ["Before noop"]
    beforeGraph.build()*.context.operationLifecycle == [OperationLifecycle.BEFORE]
  }

  def "should support an operation after the primary"() {
    given:
    def target = stage {
      context = [
          "type" : "something",
          "cloudProvider": "mock"
      ]
    }

    when:
    def afterGraph = StageGraphBuilderImpl.afterStages(target)
    subject.afterStages(target, afterGraph)

    then:
    afterGraph.build()*.name == ["After noop"]
    afterGraph.build()*.context.operationLifecycle == [OperationLifecycle.AFTER]
  }

  private class NoopClouddriverOperationStage extends CloudOperationStage {

    @Override
    String getName() {
      return "noop"
    }
  }
}
