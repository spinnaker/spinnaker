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

  void "Should correctly copy evaluated variables"() {
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

  void "Removes null&empty keys"() {
    setup:
    def stage = stage {
      refId = "1"
      type = "evaluateVariables"
      context["variables"] = [
          ["key": null, "value": null],
          ["key": null, "value": 1],
          ["key": "", "value": 1],
          ["key": "validKey", "value": "validValue"]
      ]
    }

    when:
    def result = task.execute(stage)

    then:
    result.outputs.size() == 1
    result.outputs.get("validKey") == "validValue"
  }

  void "Supports values that are complex objects"() {
    setup:
    def simpleValue = "A simple string";
    def simpleArray = ["string1", "string2", "string3"];
    def objectArray = [ [id: "15"], [id: "25"], [id: "45"] ];
    def someStuff = [
      nestedValue: 'value',
      number: 1,
      nestedHash: [
        nestedNested: 'nestedNestedValue',
        nestedArray: [1, 2, 3, 4, 5],
      ]
    ]

    def stage = stage {
      refId = "1"
      type = "evaluateVariables"
      context["variables"] = [
        ["key": "simpleValue", "value": simpleValue],
        ["key": "simpleArray", "value": simpleArray],
        ["key": "objectArray", "value": objectArray],
        ["key": "hashMap", "value": someStuff]
      ]
    }

    when:
    def result = task.execute(stage)

    then:
    result.outputs.get("simpleValue") == simpleValue
    result.outputs.get("simpleArray") == simpleArray
    result.outputs.get("objectArray") == objectArray
    result.outputs.get("hashMap") == someStuff
  }
}
