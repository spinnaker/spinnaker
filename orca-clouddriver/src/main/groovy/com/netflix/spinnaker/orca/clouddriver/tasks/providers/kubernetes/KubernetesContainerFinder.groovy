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

import com.netflix.spinnaker.orca.pipeline.model.DockerTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class KubernetesContainerFinder {
  static Map parseContainerPartsFrom(String containerName) {
    String tag = ""
    String registry = ""

    String[] parts = containerName.split('/')

    String head = parts.first()

    // Get the registry
    if (head.contains('.') || head.startsWith('localhost')) {
      registry = head
      parts = parts.drop(1)
    } else {
      throw new IllegalStateException("Could not parse a registry from the provided docker container reference")
    }

    // Get the tag
    if (parts.last().contains(':')) {
      String[] lastParts = parts.last().split(':')
      tag = lastParts.last()
      parts = parts.dropRight(1) + lastParts.first()
    } else {
      tag = "latest"
    }

    // Whatever is left is the repository
    String imageName = parts.join("/")

    return [registry: registry, tag: tag, repository: imageName ]
  }

  static void populateFromStage(Map operation, Stage stage, ArtifactResolver artifactResolver) {
    // If this is a stage in a pipeline, look in the context for the baked image.
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>

    def containers = (List<Map<String, Object>>) operation.containers

    if (!containers) {
      containers = [operation.container]
    }

    containers.forEach { container ->
      if (container.imageDescription.fromContext) {
        def image = deploymentDetails.find {
          // stageId is used here to match the source of the image to the find image stage specified by the user.
          // Previously, this was done by matching the pattern used to the pattern selected in the deploy stage, but
          // if the deploy stage's selected pattern wasn't updated before submitting the stage, this step here could fail.
          it.refId == container.imageDescription.stageId
        }

        if (image) {
          if (image.tag && image.repository) {
            return container.imageDescription = [registry: image.registry, tag: image.tag, repository: image.repository]
          } else if (image.ami) {
            return container.imageDescription = parseContainerPartsFrom(image.ami)
          }
        }
        throw new IllegalStateException("No image found in context for pattern $container.imageDescription.pattern.")
      }

      if (container.imageDescription.fromTrigger) {
        if (stage.execution.type == PIPELINE) {
          def trigger = stage.execution.trigger

          if (trigger instanceof DockerTrigger && trigger.account == container.imageDescription.account && trigger.repository == container.imageDescription.repository) {
            container.imageDescription.tag = trigger.tag
          }
        }

        if (!container.imageDescription.tag) {
          throw new IllegalStateException("No tag found for image ${container.imageDescription.registry}/${container.imageDescription.repository} in trigger context.")
        }
      }

      if (container.imageDescription.fromArtifact) {
        def resolvedArtifact = artifactResolver.getBoundArtifactForId(stage, container.imageDescription.artifactId)
        container.imageDescription.uri = resolvedArtifact.reference
      }
    }
  }
}
