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

package com.netflix.spinnaker.orca.front50.tasks

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class SetPreferredPluginReleaseTaskSpec extends Specification {

  Front50Service front50Service = Mock()

  @Subject
  SetPreferredPluginReleaseTask subject = new SetPreferredPluginReleaseTask(front50Service)

  def "should call front50"() {
    given:
    StageExecutionImpl stageExecution = stage {
      type = "whatever"
      context = [
          "pluginId": "netflix.foo",
          "preferredVersion": "1.0.1"
      ]
    }

    when:
    def result = subject.execute(stageExecution)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    1 * front50Service.setPreferredPluginVersion("netflix.foo", "1.0.1", true)
  }
}
