/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mahe.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response

@Slf4j
@Component
class DeletePropertyTask implements Task{

  @Autowired MaheService maheService

  @Override
  TaskResult execute(Stage stage) {

    List propertyIdList = stage.context.propertyIdList
    Map scope = stage.context.scope
    String env = scope.env
    String cmcTicket = stage.context.cmcTicket

    List deletedPropertyIdList = []

    propertyIdList.each { String propertyId ->
      Response response = maheService.deleteProperty(propertyId, cmcTicket, env)
      if (response.status == 200 && response.body.mimeType().startsWith('text/plain')) {
        String deletedPropetyId = response.body.in().text
        deletedPropertyIdList << deletedPropetyId
      } else {
        throw new IllegalStateException("Unable to handle $response for property $prop")
      }
    }

    def outputs = [
      deletedPropertyIdList: deletedPropertyIdList
    ]

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs)

  }
}
