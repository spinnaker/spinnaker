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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject


class DeletePluginInfoTaskSpec extends Specification {

  Front50Service front50Service = Mock()

  @Subject
  DeletePluginInfoTask task = new DeletePluginInfoTask(front50Service)

  def "Should call front50 delete plugin info"() {
    given:
    String pluginId = "netflix.stage-plugin"
    StageExecutionImpl stage = new StageExecutionImpl(new PipelineExecutionImpl(ExecutionType.ORCHESTRATION, null),
      "deletePluginInfo", [pluginInfoId: pluginId])

    when:
    def result = task.execute(stage)

    then:
    1 * front50Service.deletePluginInfo(pluginId) >> {
      Calls.response(Response.success(204, ResponseBody.create(MediaType.parse("application/json"), "")))
    }
    result.status == ExecutionStatus.SUCCEEDED
  }
}
