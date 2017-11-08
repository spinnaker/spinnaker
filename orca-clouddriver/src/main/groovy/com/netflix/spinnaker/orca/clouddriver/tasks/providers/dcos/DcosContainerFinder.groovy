/*
 * Copyright 2017 Cerner Corporation
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.dcos

import com.netflix.spinnaker.orca.pipeline.model.Stage
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class DcosContainerFinder {
  static void populateFromStage(Map operation, Stage stage) {
    // If this is a stage in a pipeline, look in the context for the baked image.
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>

    def imageDescription = (Map<String, Object>) operation.docker.image

    if (imageDescription.fromContext) {
      def image = deploymentDetails.find {
        // stageId is used here to match the source of the image to the find image stage specified by the user.
        // Previously, this was done by matching the pattern used to the pattern selected in the deploy stage, but
        // if the deploy stage's selected pattern wasn't updated before submitting the stage, this step here could fail.
        it.refId == imageDescription.stageId
      }
      if (!image) {
        throw new IllegalStateException("No image found in context for pattern $imageDescription.pattern.")
      } else {
        imageDescription.registry = image.registry
        imageDescription.tag = image.tag
        imageDescription.repository = image.repository
        imageDescription.imageId = buildImageId(image.registry, image.repository, image.tag)
      }
    }

    if (imageDescription.fromTrigger) {
      if (stage.execution.type == PIPELINE) {
        Map trigger = stage.execution.trigger

        if (trigger?.account == imageDescription.account && trigger?.repository == imageDescription.repository) {
          imageDescription.tag = trigger.tag
        }

        imageDescription.imageId = buildImageId(imageDescription.registry, imageDescription.repository, imageDescription.tag)
      }

      if (!imageDescription.tag) {
        throw new IllegalStateException("No tag found for image ${imageDescription.registry}/${imageDescription.repository} in trigger context.")
      }
    }
  }

  static String buildImageId(Object registry, Object repo, Object tag) {
    if (registry) {
      return "$registry/$repo:$tag"
    } else {
      return "$repo:$tag"
    }
  }
}
