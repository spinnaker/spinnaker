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
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.DockerTrigger
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import retrofit.client.Response

import javax.annotation.Nullable
import org.springframework.stereotype.Component
import com.fasterxml.jackson.core.type.TypeReference
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.time.Duration
import java.util.function.Supplier
import java.util.stream.StreamSupport
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper

@Slf4j
@Component
class EcsServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {
  final private OortService oortService
  private final ContextParameterProcessor contextParameterProcessor
  private static final ThreadLocal<Yaml> yamlParser =
      ThreadLocal.withInitial({ -> new Yaml(new SafeConstructor()) })

  final String cloudProvider = "ecs"
  final boolean katoResultExpected = false

  final Optional<String> healthProviderName = Optional.of("ecs")

  final ObjectMapper mapper = new ObjectMapper()
  final ArtifactUtils artifactUtils
  private static final ObjectMapper objectMapper = OrcaObjectMapper.getInstance()
  private final RetrySupport retrySupport

  @Autowired
  EcsServerGroupCreator(ArtifactUtils artifactUtils, OortService oort, ContextParameterProcessor contextParameterProcessor, RetrySupport retrySupport) {
    this.artifactUtils = artifactUtils
    this.oortService = oort
    this.contextParameterProcessor = contextParameterProcessor
    this.retrySupport = retrySupport
  }

  @Override
  List<Map> getOperations(StageExecution stage) {
    def operation = [:]

    operation.putAll(stage.context)

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    if (operation.useTaskDefinitionArtifact) {
      if (operation.taskDefinitionArtifact) {
        operation.resolvedTaskDefinitionArtifact = getTaskDefArtifact(stage, operation.taskDefinitionArtifact)
        if (operation.evaluateTaskDefinitionArtifactExpressions) {
          Iterable<Object> rawArtifact =
              retrySupport.retry(
                  fetchAndParseArtifact(operation.resolvedTaskDefinitionArtifact), 10, Duration.ofMillis(200), true)

          List<Map<Object, Object>> unevaluatedArtifact =
              StreamSupport.stream(rawArtifact.spliterator(), false)
                  .filter(Objects.&nonNull)
                  .map(this.&coerceArtifactToList)
                  .collect()
                  .flatten()

          Map<Object, Object> evaluatedArtifact = getSpelEvaluatedArtifact(unevaluatedArtifact, stage)

          operation.spelProcessedTaskDefinitionArtifact = evaluatedArtifact
        }
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

  private Supplier<Iterable<Object>> fetchAndParseArtifact(Artifact artifact) {
    return { ->
      Response artifactText = oortService.fetchArtifact(artifact)
      try {
        if(artifactText != null && artifactText.getBody() != null){
          return yamlParser.get().loadAll(artifactText.getBody().in());
        } else{
          throw new ConfigurationException("Invalid artifact configuration or task definition artifact is null")
        }
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private List<Map<Object, Object>> coerceArtifactToList(Object artifact) {
    Map<Object, Object> singleArtifact =
        objectMapper.convertValue(artifact, new TypeReference<Map<Object, Object>>() {})
    return List.of(singleArtifact)
  }

  private Map<Object, Object> getSpelEvaluatedArtifact(
      List<Map<Object, Object>> unevaluatedArtifact, StageExecution stage) {
    Map<String, Object> processorInput = ImmutableMap.of("artifact", unevaluatedArtifact);

    Map<String, Object> processorResult =
        contextParameterProcessor.process(
            processorInput,
            contextParameterProcessor.buildExecutionContext(stage),
            true)

    if ((boolean) stage.getContext().getOrDefault("failOnFailedExpressions", false)
        && processorResult.containsKey(PipelineExpressionEvaluator.SUMMARY)) {
      throw new IllegalStateException(
          String.format(
              "Failure evaluating Artifact expressions: %s",
              processorResult.get(PipelineExpressionEvaluator.SUMMARY)))
    }
    return processorResult.get("artifact").get(0)
  }

  static String buildImageId(Object registry, Object repo, Object tag) {
    if (registry) {
      return "$registry/$repo:$tag"
    } else {
      return "$repo:$tag"
    }
  }

  private Artifact getTaskDefArtifact(StageExecution stage, Object input) {
    TaskDefinitionArtifact taskDefArtifactInput = mapper.convertValue(input, TaskDefinitionArtifact.class)

    Artifact taskDef = artifactUtils.getBoundArtifactForStage(
      stage,
      taskDefArtifactInput.artifactId,
      taskDefArtifactInput.artifact)
    if (taskDef == null) {
      throw new IllegalArgumentException("Unable to bind the task definition artifact");
    }
    return taskDef
  }

  private Map<String, String> getContainerToImageMap(ArrayList<Map<String, Object>> mappings, StageExecution stage) {
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

  private String getImageAddressFromDescription(Map<String, Object> description, StageExecution givenStage) {
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
