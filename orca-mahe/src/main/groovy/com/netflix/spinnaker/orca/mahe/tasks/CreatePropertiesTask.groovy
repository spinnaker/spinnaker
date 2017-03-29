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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.PropertyAction
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

@Slf4j
@Component
class CreatePropertiesTask implements Task {

  @Autowired MaheService maheService
  @Autowired ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    List properties = assemblePersistedPropertyListFromContext(stage.context, stage.context.persistedProperties)
    List originalProperties = assemblePersistedPropertyListFromContext(stage.context, stage.context.originalProperties)
    List propertyIdList = []
    PropertyAction propertyAction = PropertyAction.UNKNOWN

    properties.forEach { Map prop ->
      Response response
      if (stage.context.delete) {
        log.info("Deleting Property: ${prop.property.propertyId} on execution ${stage.execution.id}")
        response = maheService.deleteProperty(prop.property.propertyId, 'delete', prop.property.env)
        propertyAction = PropertyAction.DELETE
      } else {
        log.info("Upserting Property: ${prop} on execution ${stage.execution.id}")
        response = maheService.upsertProperty(prop)
        propertyAction = prop.property.propertyId ? PropertyAction.UPDATE : PropertyAction.CREATE
      }

      if (response.status == 200) {
        if (response.body?.mimeType()?.startsWith('application/')) {
          propertyIdList << mapper.readValue(response.body.in().text, Map)
        }
      } else {
        throw new IllegalStateException("Unable to handle $response for property $prop")
      }
    }

    boolean rollback = stage.context.rollbackProperties

    def outputs = [
      propertyIdList: propertyIdList,
      originalProperties: originalProperties,
      rollback: rollback,
      propertyAction: propertyAction,
    ]

    return new TaskResult(SUCCEEDED, outputs, outputs)

  }



  List assemblePersistedPropertyListFromContext(Map<String, Object> context, List propertyList) {
    Map scope = context.scope
    scope.appId = scope.appIdList.join(',')
    String email = context.email
    String cmcTicket = context.cmcTicket

    return propertyList.collect { Map prop ->
      if(prop) {
        prop << scope
        prop.email = email
        prop.sourceOfUpdate = 'spinnaker'
        prop.cmcTicket = cmcTicket
        [property: prop]
      }
    }
  }

}
