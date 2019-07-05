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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.DockerTrigger
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import groovy.util.logging.Slf4j
import javax.annotation.Nullable
import org.springframework.stereotype.Component

@Slf4j
@Component
class EcsServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  final String cloudProvider = "ecs"
  final boolean katoResultExpected = false

  final Optional<String> healthProviderName = Optional.of("ecs")

  final ObjectMapper mapper = new ObjectMapper()
  final ArtifactResolver artifactResolver

  EcsServerGroupCreator(ArtifactResolver artifactResolver) {
    this.artifactResolver = artifactResolver
  }

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    operation.putAll(stage.context)

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    if (operation.useTaskDefinitionArtifact) {
      if (operation.taskDefinitionArtifact) {
        operation.resolvedTaskDefinitionArtifact = getTaskDefArtifact(stage, operation.taskDefinitionArtifact)
      } else {
        throw new IllegalStateException("No task definition artifact found in context for operation.")
      }

      // container mappings are required for artifacts, so we know which container(s) get which images
      if (operation.containerMappings) {
        def containerMappings = (ArrayList<Map<String, Object>>) operation.containerMappings
        operation.containerToImageMap = getContainerToImageMap(containerMappings, stage)
      } else {
        throw new IllegalStateException("No container mappings for task definition artifact found in context for operation.")
      }
    }

    def imageDescription = (Map<String, Object>) operation.imageDescription

    if (imageDescription) {
      operation.dockerImageAddress = getImageAddressFromDescription(imageDescription, stage)
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

  private Artifact getTaskDefArtifact(Stage stage, Object input) {
    TaskDefinitionArtifact taskDefArtifactInput = mapper.convertValue(input, TaskDefinitionArtifact.class)

    Artifact taskDef = artifactResolver.getBoundArtifactForStage(
      stage,
      taskDefArtifactInput.artifactId,
      taskDefArtifactInput.artifact)
    if (taskDef == null) {
      throw new IllegalArgumentException("Unable to bind the task definition artifact");
    }
    return taskDef
  }

  private Map<String, String> getContainerToImageMap(ArrayList<Map<String, Object>> mappings, Stage stage) {
    def containerToImageMap = [:]

    // each mapping should be in the shape { containerName: "", imageDescription: {}}
    mappings.each{
      def imageValue = (Map<String, Object>) it.imageDescription
      def resolvedImageAddress = getImageAddressFromDescription(imageValue, stage)
      def name = (String) it.containerName
      containerToImageMap.put(name, resolvedImageAddress)
    }
    return containerToImageMap
  }

  private String getImageAddressFromDescription(Map<String, Object> description, Stage givenStage) {
    if (description.fromContext) {
      if (givenStage.execution.type == ExecutionType.ORCHESTRATION) {
        // Use image from specific "find image from tags" stage
        def imageStage = givenStage.findAncestor({
          return it.context.containsKey("amiDetails") && it.refId == description.stageId
        })

        if (!imageStage) {
          throw new IllegalStateException("No image stage found in context for $description.imageLabelOrSha.")
        }

        description.imageId = imageStage.context.amiDetails.imageId.value.get(0).toString()
      }
    }

    if (description.fromTrigger) {
      if (givenStage.execution.type == ExecutionType.PIPELINE) {
        def trigger = givenStage.execution.trigger

        if (trigger instanceof DockerTrigger && trigger.account == description.account && trigger.repository == description.repository) {
          description.tag = trigger.tag
        }
        description.imageId = buildImageId(description.registry, description.repository, description.tag)
      }

      if (!description.tag) {
        throw new IllegalStateException("No tag found for image ${description.registry}/${description.repository} in trigger context.")
      }
    }

    if (!description.imageId) {
      description.imageId = buildImageId(description.registry, description.repository, description.tag)
    }

    return description.imageId
  }

  private static class TaskDefinitionArtifact {
    @Nullable public String artifactId
    @Nullable public Artifact artifact
  }
}
