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
import com.netflix.spinnaker.orca.RetrySupport
import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.PropertyAction
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
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
  @Autowired RetrySupport retrySupport

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
            stage.context.persistedProperties.each { Map prop ->
              String propertyId = prop.propertyId
              if (shouldRollback(prop)) {
                log.info("Rolling back the creation of: ${propertyId} on execution ${execution.id} by deleting")
                Response response = mahe.deleteProperty(propertyId, "spinnaker rollback", extractEnvironment(propertyId))
                resolveRollbackResponse(response, stage.context.propertyAction.toString(), prop)
              } else {
                log.info("Property ${propertyId} has been updated since this execution (${execution.id}); not rolling back create")
              }
            }
            break
          case PropertyAction.UPDATE.toString():
            stage.context.originalProperties.each { Map originalProp ->
              Map property = originalProp.property
              Map updatedProperty = (Map) stage.context.persistedProperties.find { it.propertyId == property.propertyId }
              String propertyId = property.propertyId
              if (shouldRollback(updatedProperty)) {
                log.info("Rolling back the update of: ${propertyId} on execution ${execution.id} by upserting")
                Response response = mahe.upsertProperty(originalProp)
                resolveRollbackResponse(response, stage.context.propertyAction.toString(), property)
              } else {
                log.info("Property ${propertyId} has been updated since this execution (${execution.id}); not rolling back update")
              }
            }
            break
          case PropertyAction.DELETE.toString():
            stage.context.originalProperties.each { Map prop ->
              Map property = prop.property
              if (propertyExists(property)) {
               log.info("Property ${property.propertyId} exists, not restoring to original state after delete.")
              } else {
                if (property.propertyId) {
                  property.remove('propertyId')
                }
                log.info("Rolling back the delete of: ${property.key}|${property.value} on execution ${execution.id} by re-creating")

                Response response = mahe.upsertProperty(prop)
                resolveRollbackResponse(response, stage.context.propertyAction.toString(), property)
              }
            }
        }
      }
    }
  }

  private boolean shouldRollback(Map property) {
    String propertyId = property.propertyId
    String env = extractEnvironment(propertyId)
    try {
      return retrySupport.retry({
        Response propertyResponse = mahe.getPropertyById(propertyId, env)
        Map currentProperty = mapper.readValue(propertyResponse.body.in().text, Map)
        return currentProperty.property.ts == property.ts
      }, 3, 2, false)
    } catch (RetrofitError error) {
      if (error.response.status == 404) {
        return false
      }
      throw error
    }
  }

  private boolean propertyExists(Map<String, String> property) {
    try {
      return retrySupport.retry({
        mahe.getPropertyById(property.propertyId, property.env)
        return true
      }, 3, 2, false)
    } catch (RetrofitError error) {
      if (error.kind == RetrofitError.Kind.HTTP && error.response.status == 404) {
        return false
      }
      throw error
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
