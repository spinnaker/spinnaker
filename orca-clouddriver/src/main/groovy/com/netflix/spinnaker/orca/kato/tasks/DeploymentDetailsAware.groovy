/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

/**
 * Tasks may implement this trait to get convention-based access to deployment details that should come from in order of preference:
 * <ol>
 *     <li>an upstream stage on the same parallel branch or on a parent pipeline branch.
 *     <li>the global context of the pipeline or of a parent pipeline.
 * </ol>
 */
trait DeploymentDetailsAware {

  private ObjectMapper pipelineObjectMapper = OrcaObjectMapper.getInstance()

  void withImageFromPrecedingStage(
    Stage stage,
    String targetRegion,
    String targetCloudProvider,
    Closure callback) {
    Stage previousStage = getPreviousStageWithImage(stage, targetRegion, targetCloudProvider)
    def result = [:]
    if (previousStage && isCloudProviderEqual(stage, previousStage)) {
      if (previousStage.context.containsKey("amiDetails")) {
        def amiDetail = previousStage.context.amiDetails.find {
          !targetRegion || it.region == targetRegion
        } ?: previousStage.context.amiDetails[0]
        result.amiName = amiDetail.ami
        result.imageId = amiDetail.imageId
      } else {
        result.amiName = previousStage.context.ami
        result.imageId = previousStage.context.imageId
      }
      callback(result)
    }
  }

  Stage getPreviousStageWithImage(Stage stage, String targetRegion, String targetCloudProvider) {
    if (stage.execution.type == ORCHESTRATION) {
      return null
    }

    return getAncestors(stage, stage.execution).find {
      def regions = (it.context.region ? [it.context.region] : it.context.regions) as Set<String>
      def cloudProviderFromContext = it.context.cloudProvider ?: it.context.cloudProviderType
      boolean hasTargetCloudProvider = !cloudProviderFromContext || targetCloudProvider == cloudProviderFromContext
      boolean hasTargetRegion = !targetRegion || regions?.contains(targetRegion) || regions?.contains("global")
      boolean hasImage = it.context.containsKey("ami") || it.context.containsKey("amiDetails")

      return hasImage && hasTargetRegion && hasTargetCloudProvider
    }
  }

  List<Execution> getPipelineExecutions(Execution execution) {
    if (execution?.type == PIPELINE) {
      return [execution] + getPipelineExecutions(getParentPipelineExecution(execution))
    } else {
      return []
    }
  }

  boolean isCloudProviderEqual(Stage stage, Stage previousStage){
    if(previousStage.context.cloudProvider!=null && stage.context.cloudProvider!=null) {
      return previousStage.context.cloudProvider == stage.context.cloudProvider
    }
    return true
  }

  List<Stage> getAncestors(Stage stage, Execution execution) {
    if (stage?.requisiteStageRefIds) {
      def previousStages = execution.stages.findAll {
        it.refId in stage.requisiteStageRefIds

      }
      def syntheticStages = execution.stages.findAll {
        it.parentStageId in previousStages*.id
      }
      return (previousStages + syntheticStages) + previousStages.collect { getAncestors(it, execution ) }.flatten()
    } else if (stage?.parentStageId) {
      def parent = execution.stages.find { it.id == stage.parentStageId }
      return ([parent] + getAncestors(parent, execution)).flatten()
    } else if (execution.type == PIPELINE) {
      def parentPipelineExecution = getParentPipelineExecution(execution)

      if (parentPipelineExecution) {
        String parentPipelineStageId = (execution.trigger as PipelineTrigger).parentPipelineStageId
        Stage parentPipelineStage = parentPipelineExecution.stages?.find {
          it.type == "pipeline" && it.id == parentPipelineStageId
        }

        if (parentPipelineStage) {
          return getAncestors(parentPipelineStage, parentPipelineExecution)
        } else {
          List<Stage> parentPipelineStages = parentPipelineExecution.stages?.collect()?.sort {
            a, b -> b.endTime <=> a.endTime
          }

          if (parentPipelineStages) {
            // The list is sorted in reverse order by endTime.
            Stage firstStage = parentPipelineStages.last()

            return parentPipelineStages + getAncestors(firstStage, parentPipelineExecution)
          } else {
            // Parent pipeline has no stages.
            return getAncestors(null, parentPipelineExecution)
          }
        }
      }

      return []
    } else {
      return []
    }
  }

  private Execution getParentPipelineExecution(Execution execution) {
    // The initial stage execution is a Pipeline, and the ancestor executions are Maps.
    if (execution.type == PIPELINE && execution.trigger instanceof PipelineTrigger) {
      return (execution.trigger as PipelineTrigger).parentExecution
    }
    return null
  }

  void withImageFromDeploymentDetails(
    Stage stage,
    String targetRegion,
    String targetCloudProvider,
    Closure callback) {
    def result = [:]
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>

    if (!deploymentDetails) {
      // If no deployment details were found in the stage context, check outputs of each stage of each pipeline up the tree.
      List<Execution> pipelineExecutions = getPipelineExecutions(stage.execution)

      deploymentDetails = pipelineExecutions.findResult { execution ->
        execution.stages.findResult {
          it.outputs.deploymentDetails
        }
      }
    }

    if (deploymentDetails) {
      result.amiName = deploymentDetails.find {
        (!targetRegion || it.region == targetRegion || it.region == "global") &&
        (targetCloudProvider == it.cloudProvider || targetCloudProvider == it.cloudProviderType)
      }?.ami
      // docker image ids are not region or cloud provider specific so no need to filter by region
      // But, if both the dependent stage and the deployment details specify cloud provider, makes no sense to return
      // the details unless they match. Without this guard, multi-provider pipelines are more challenging.
      def firstDeploymentDetails = deploymentDetails.first()
      def firstDeploymentDetailsCloudProvider = firstDeploymentDetails.cloudProvider ?: firstDeploymentDetails.cloudProviderType
      if (!firstDeploymentDetailsCloudProvider || !targetCloudProvider || firstDeploymentDetailsCloudProvider == targetCloudProvider) {
        result.imageId = firstDeploymentDetails.imageId
      }
      callback(result)
    }
  }

}
