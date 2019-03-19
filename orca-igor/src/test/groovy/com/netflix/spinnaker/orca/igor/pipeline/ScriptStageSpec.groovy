/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.igor.pipeline

import com.netflix.spinnaker.orca.igor.tasks.GetBuildPropertiesTask
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ScriptStageSpec extends Specification {
  def "should fetch properties after running the script"() {
    given:
    def scriptStage = new ScriptStage()

    def stage = stage {
      type = "script"
      context = [
        command: "echo hello",
      ]
    }

    when:
    def tasks = scriptStage.buildTaskGraph(stage)

    then:
    tasks.findAll {
      it.implementingClass == GetBuildPropertiesTask
    }.size() == 1
  }
}
