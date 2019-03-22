/*
 * Copyright 2016 Lookout Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.DockerTrigger
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class EcsServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  final String cloudProvider = "ecs"
  final boolean katoResultExpected = false

  final Optional<String> healthProviderName = Optional.of("ecs")

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    operation.putAll(stage.context)

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    def imageDescription = (Map<String, Object>) operation.imageDescription

    if (imageDescription) {
      if (imageDescription.fromContext) {
        if (stage.execution.type == ExecutionType.ORCHESTRATION) {
          // Use image from specific "find image from tags" stage
          def imageStage = getAncestors(stage, stage.execution).find {
            it.refId == imageDescription.stageId && it.context.containsKey("amiDetails")
          }

          if (!imageStage) {
            throw new IllegalStateException("No image stage found in context for $imageDescription.imageLabelOrSha.")
          }

          imageDescription.imageId = imageStage.context.amiDetails.imageId.value.get(0).toString()
        }
      }

      if (imageDescription.fromTrigger) {
        if (stage.execution.type == ExecutionType.PIPELINE) {
          def trigger = stage.execution.trigger

          if (trigger instanceof DockerTrigger && trigger.account == imageDescription.account && trigger.repository == imageDescription.repository) {
            imageDescription.tag = trigger.tag
          }

          imageDescription.imageId = buildImageId(imageDescription.registry, imageDescription.repository, imageDescription.tag)
        }

        if (!imageDescription.tag) {
          throw new IllegalStateException("No tag found for image ${imageDescription.registry}/${imageDescription.repository} in trigger context.")
        }
      }

      if (!imageDescription.imageId) {
        imageDescription.imageId = buildImageId(imageDescription.registry, imageDescription.repository, imageDescription.tag)
      }

      operation.dockerImageAddress = imageDescription.imageId
    } else if (!operation.dockerImageAddress) {
      // Fall back to previous behavior: use image from any previous "find image from tags" stage by default
      def bakeStage = getPreviousStageWithImage(stage, operation.region, cloudProvider)

      if (bakeStage) {
        operation.dockerImageAddress = bakeStage.context.amiDetails.imageId.value.get(0).toString()
      }
    }

    return [[(ServerGroupCreator.OPERATION): operation]]
  }

  static String buildImageId(Object registry, Object repo, Object tag) {
    if (registry) {
      return "$registry/$repo:$tag"
    } else {
      return "$repo:$tag"
    }
  }
}
