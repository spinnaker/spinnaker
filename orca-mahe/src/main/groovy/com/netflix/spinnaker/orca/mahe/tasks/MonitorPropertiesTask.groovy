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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.apache.http.HttpStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response

@Slf4j
@Component
class MonitorPropertiesTask implements Task{
  @Autowired MaheService maheService
  @Autowired ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {

    Map context = stage.context
    log.info("MonitorPropertiesTask context: $context")
    List propertyIds = context.propertyIdList*.propertyId
    log.info("propertyIds: $propertyIds")

    List fetchedProperties = []

    Map outputs = [
      persistedProperties: fetchedProperties
    ]

    propertyIds.forEach { String id ->
      try {
        Response response = maheService.getPropertyById(id, context.scope.env)
        if (response.status == HttpStatus.SC_OK) {
          Map responseMap = mapper.readValue(response.body.in().text, Map)
          fetchedProperties << responseMap.property
        }

      } catch (RetrofitError e ){
        log.error("Exception occurred while getting persisted property with id ${id} from mahe service", e)
        return new TaskResult(ExecutionStatus.RUNNING, outputs)
      }
    }


    if(outputs.persistedProperties.size() == propertyIds.size()) {
      return new TaskResult(ExecutionStatus.SUCCEEDED, outputs, outputs)
    }

    log.info("create persited properties in progress: ${outputs}")
    return new TaskResult(ExecutionStatus.RUNNING, outputs)
  }
}
