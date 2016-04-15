/*
 * Copyright 2015 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class CloudFoundryServerGroupCreator implements ServerGroupCreator {

  boolean katoResultExpected = false
  String cloudProvider = "cf"

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    if (stage.execution.hasProperty('trigger')) {
      operation.trigger = stage.execution.trigger
    }

    // If this is a stage in a pipeline, look in the context for the baked image.
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>

    if (!operation.image && deploymentDetails) {
      // Bakery ops are keyed off cloudProviderType
      operation.image = deploymentDetails.find { it.cloudProviderType == 'cf' }?.imageId

      // Alternatively, FindImage ops distinguish between server groups deployed to different zones.
      // This is partially because AWS images are only available regionally.
      if (!operation.image && stage.context.zone) {
        operation.image = deploymentDetails.find { it.zone == stage.context.zone }?.imageId
      }

      // Lastly, fall back to any image within deploymentDetails, so long as it's unambiguous.
      if (!operation.image) {
        if (deploymentDetails.size() != 1) {
          throw new IllegalStateException("Ambiguous choice of deployment images found for deployment to " +
                  "'${stage.context.zone}'. Images found from cluster in " +
                  "${deploymentDetails.collect{it.zone}.join(",") } - " +
                  "only 1 should be available.")
        }
        operation.image = deploymentDetails[0].imageId
      }
    }

    if (!operation.image && !operation.repository && !operation.artifact) {
      throw new IllegalStateException("Neither an image nor a repository/artifact could be found in ${stage.context.zone}.")
    }


    return [[(ServerGroupCreator.OPERATION): operation]]
  }
}
