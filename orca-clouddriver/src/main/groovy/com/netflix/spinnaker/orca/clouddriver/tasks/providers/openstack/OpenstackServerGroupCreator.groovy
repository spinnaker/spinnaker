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
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

/**
 * Create an openstack createServerGroup operation, potentially injecting the image to use
 * from the pipeline context.
 */
@Slf4j
@Component
class OpenstackServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  boolean katoResultExpected = false
  String cloudProvider = 'openstack'

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    // If this stage was synthesized by a parallel deploy stage, the operation properties will be under 'cluster'.
    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    //let's not throw NPE's here, even if the request is invalid
    operation.serverGroupParameters = operation.serverGroupParameters ?: [:]

    withImageFromPrecedingStage(stage, operation.region, cloudProvider) {
      operation.serverGroupParameters.image = operation.serverGroupParameters.image ?: it.imageId
    }

    withImageFromDeploymentDetails(stage, operation.region, cloudProvider) {
      operation.serverGroupParameters.image = operation.serverGroupParameters.image ?: it.imageId
    }

    if (!operation.serverGroupParameters.image) {
      throw new IllegalStateException("No image could be found in ${stage.context.region}.")
    }

    return [[(OPERATION): operation]]
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.of("Openstack")
  }
}
