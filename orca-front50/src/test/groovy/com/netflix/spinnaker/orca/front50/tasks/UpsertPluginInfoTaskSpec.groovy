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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.PluginInfo
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class UpsertPluginInfoTaskSpec extends Specification {

  Front50Service front50Service = Mock()

  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  UpsertPluginInfoTask task = new UpsertPluginInfoTask(front50Service, objectMapper)

  def "Should call front50 to upsert a plugin info"() {
    given:
    PluginInfo pluginInfo = PluginInfo.builder()
      .id("netflix.stage-plugin")
      .description("A stage plugin")
      .provider("netflix")
      .releases([new PluginInfo.Release()])
      .build()

    Stage stage = new Stage(new Execution(Execution.ExecutionType.ORCHESTRATION, null),
      "upsertPluginInfo", [pluginInfo: pluginInfo])

    when:
    def result = task.execute(stage)

    then:
    1 * front50Service.upsertPluginInfo(pluginInfo) >> pluginInfo
    result.status == ExecutionStatus.SUCCEEDED
  }
}
