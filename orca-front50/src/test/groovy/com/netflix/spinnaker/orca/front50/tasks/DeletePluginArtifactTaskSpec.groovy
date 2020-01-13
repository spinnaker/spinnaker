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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject


class DeletePluginArtifactTaskSpec extends Specification {

  Front50Service front50Service = Mock()

  @Subject
  DeletePluginArtifactTask task = new DeletePluginArtifactTask(front50Service)

  def "Should call front50 delete plugin artifact"() {
    given:
    String pluginId = "netflix.stage-plugin"
    Stage stage = new Stage(new Execution(Execution.ExecutionType.ORCHESTRATION, null),
      "deletePluginArtifact", [pluginArtifactId: pluginId])

    when:
    def result = task.execute(stage)

    then:
    1 * front50Service.deletePluginArtifact(pluginId) >> {
      new Response('http://front50', 204, 'No Content', [], null)
    }
    result.status == ExecutionStatus.SUCCEEDED
  }
}
