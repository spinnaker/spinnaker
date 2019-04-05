/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.clone

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Netflix bakes images in their test account. This rigmarole is to allow the
 * prod account access to that image.
 */
@Slf4j
@Component
class BakeryImageAccessDescriptionDecorator implements CloneDescriptionDecorator {
  @Value('${default.bake.account:default}')
  String defaultBakeAccount

  @Override
  boolean shouldDecorate(Map operation) {
    Collection<String> targetRegions = targetRegions(operation)

    return getCloudProvider(operation) == "aws" && // the operation is a clone of stage.context.
      operation.credentials != defaultBakeAccount &&
      targetRegions &&
      operation.amiName
  }

  @Override
  void decorate(Map<String, Object> operation, List<Map<String, Object>> descriptions, Stage stage) {
    def allowLaunchDescriptions = targetRegions(operation).collect { String region ->
      [
        allowLaunchDescription: [
          account    : operation.credentials,
          credentials: defaultBakeAccount,
          region     : region,
          amiName    : operation.amiName
        ]
      ]
    }
    descriptions.addAll(allowLaunchDescriptions)

    log.info("Generated `allowLaunchDescriptions` (allowLaunchDescriptions: ${allowLaunchDescriptions})")
  }

  private static Collection<String> targetRegions(Map operation) {
    return operation.region ? [operation.region] :
      operation.availabilityZones ? operation.availabilityZones.keySet() : []
  }
}
