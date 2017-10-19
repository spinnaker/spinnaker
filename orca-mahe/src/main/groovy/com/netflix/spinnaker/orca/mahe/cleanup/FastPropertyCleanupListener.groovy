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

package com.netflix.spinnaker.orca.mahe.cleanup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.PropertyAction
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response

@Slf4j
@Component
class FastPropertyCleanupListener implements ExecutionListener {

  private final MaheService mahe

  @Autowired
  FastPropertyCleanupListener(MaheService mahe) {
    this.mahe = mahe
  }

  @Autowired ObjectMapper mapper

  @Override
  void afterExecution(Persister persister,
                      Execution execution,
                      ExecutionStatus executionStatus,
                      boolean wasSuccessful) {

    boolean rollbackBasedOnExecutionStatus = executionStatus in [ExecutionStatus.TERMINAL, ExecutionStatus.CANCELED]
    List<Stage> rollbacks = rollbackBasedOnExecutionStatus ? execution.stages : execution.stages.findAll { it.context.rollback }

    if (!rollbacks.empty) {
      rollbacks.each { stage ->
        switch (stage.context.propertyAction) {
          case PropertyAction.CREATE.toString():
            stage.context.propertyIdList.each { prop ->
              log.info("Rolling back the creation of: ${prop.propertyId} on execution ${execution.id} by deleting")
              Response response = mahe.deleteProperty(prop.propertyId, "spinnaker rollback", extractEnvironment(prop.propertyId))
              resolveRollbackResponse(response, stage.context.propertyAction.toString(), prop)
            }
            break
          case PropertyAction.UPDATE.toString():
            stage.context.originalProperties.each { prop ->
              log.info("Rolling back the ${stage.context.propertyAction} of: ${prop.property.propertyId} on execution ${execution.id} by upserting")
              Response response = mahe.upsertProperty(prop)
              resolveRollbackResponse(response, stage.context.propertyAction.toString(), prop.property)
            }
            break
          case PropertyAction.DELETE.toString():
            stage.context.originalProperties.each { prop ->
              if (prop.property.propertyId) {
                prop.property.remove('propertyId')
              }
              log.info("Rolling back the ${stage.context.propertyAction} of: ${prop.property.key}|${prop.property.value} on execution ${execution.id} by re-creating")

              Response response = mahe.upsertProperty(prop)
              resolveRollbackResponse(response, stage.context.propertyAction.toString(), prop.property)
            }
        }
      }
    }
  }

  private void resolveRollbackResponse(Response response, String initialPropertyAction, def property) {
    if(response.status == 200) {
      log.info("Successful Fast Property rollback for $initialPropertyAction")
      if (response.body?.mimeType()?.startsWith('application/')) {
        def json = mapper.readValue(response.body.in().text, Map)
        log.info("Fast Property rollback response: $json")
      }
    } else {
      throw new IllegalStateException("Unable to rollback ${initialPropertyAction} with $response for property $property")
    }
  }

  private String extractEnvironment(propertyId) {
    propertyId.find(~/\w+\|\w+\|(\w+)\|.*?/) { match, env ->
      env
    }
  }
}
