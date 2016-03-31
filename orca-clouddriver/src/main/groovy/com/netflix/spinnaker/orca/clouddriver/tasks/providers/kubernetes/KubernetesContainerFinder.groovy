/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage

class KubernetesContainerFinder {
  static void populateFromStage(Map operation, Stage stage) {
    // If this is a stage in a pipeline, look in the context for the baked image.
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>

    def containers = (List<Map<String, Object>>) operation.containers

    containers.forEach { container ->
      if (container.imageDescription.fromContext) {
        def image = deploymentDetails.find {
          // stageId is used here to match the source of the image to the find image stage specified by the user.
          // Previously, this was done by matching the pattern used to the pattern selected in the deploy stage, but
          // if the deploy stage's selected pattern wasn't updated before submitting the stage, this step here could fail.
          it.refId == container.imageDescription.stageId
        }
        if (!image) {
          throw new IllegalStateException("No image found in context for pattern $container.imageDescription.pattern.")
        } else {
          container.imageDescription = [registry: image.registry, tag: image.tag, repository: image.repository]
        }
      }

      if (container.imageDescription.fromTrigger) {
        if (stage.execution instanceof Pipeline) {
          Map trigger = ((Pipeline) stage.execution).trigger

          def matchingTag = trigger?.buildInfo?.taggedImages?.findResult { info ->
            if (container.imageDescription.registry == info.getAt("registry") &&
                container.imageDescription.repository == info.getAt("repository")) {
              return info.tag
            }
          }

          container.imageDescription.tag = matchingTag
        }

        if (!container.imageDescription.tag) {
          throw new IllegalStateException("No tag found for image ${container.imageDescription.registry}/${container.imageDescription.repository} in trigger context.")
        }
      }
    }
  }
}
