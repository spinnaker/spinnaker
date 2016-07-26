/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.openstack

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

/**
 * Create an openstack createServerGroup operation, potentially injecting the image to use
 * from the pipeline context.
 */
@Slf4j
@Component
class OpenstackServerGroupCreator implements ServerGroupCreator {

  boolean katoResultExpected = false
  String cloudProvider = 'openstack'

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    // If this stage was synthesized by a parallel deploy stage, the operation properties will be under 'cluster'.
    if (stage.context.containsKey('cluster')) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    // If this is a stage in a pipeline, look in the context for the baked image.
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>

    //let's not throw NPE's here, even if the request is invalid
    operation.serverGroupParameters = operation.serverGroupParameters ?: [:]
    if (!operation.serverGroupParameters.image && deploymentDetails) {
      // Bakery ops are keyed off cloudProviderType
      operation.serverGroupParameters.image = deploymentDetails.find { it.cloudProviderType == 'openstack' }?.imageId

      // Alternatively, FindImage ops distinguish between server groups deployed to different regions.
      // This is partially because openstack images are only available regionally.
      if (!operation.serverGroupParameters.image && stage.context.region) {
        operation.serverGroupParameters.image = deploymentDetails.find { it.region == stage.context.region }?.imageId
      }

      // Lastly, fall back to any image within deploymentDetails, so long as it's unambiguous.
      if (!operation.serverGroupParameters.image) {
        if (deploymentDetails.size() != 1) {
          throw new IllegalStateException('Ambiguous choice of deployment images found for deployment to ' +
            "'${stage.context.region}'. Images found from cluster in " +
            "${deploymentDetails.collect{it.region}.join(",") } - " +
            'only 1 should be available.')
        }
        operation.serverGroupParameters.image = deploymentDetails[0].imageId
      }
    }

    if (!operation.serverGroupParameters.image) {
      throw new IllegalStateException("No image could be found in ${stage.context.region}.")
    }

    return [[(OPERATION): operation]]
  }
}
