/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.tasks

import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class EvaluateVariablesTaskSpec extends Specification {

  @Subject
  task = new EvaluateVariablesTask()

  void "Should correctly evaulate variables"() {
    setup:
    def stage = stage {
      refId = "1"
      type = "evaluateVariables"
      context["variables"] = [
        ["key": "myKey1", "value": "ohWow"],
        ["key": "myKey2", "value": "myMy"]
      ]
    }

    when:
    def result = task.execute(stage)

    then:
    result.outputs.get("myKey1") == "ohWow"
    result.outputs.get("myKey2") == "myMy"
  }
}
