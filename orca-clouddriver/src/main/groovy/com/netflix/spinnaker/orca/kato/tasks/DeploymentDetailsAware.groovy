package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.pipeline.model.Stage

/**
 * Tasks may implement this trait to get convention-based access to deployment details that should come from in order of preference:
 * <ol>
 *     <li>an upstream stage on the same parallel branch.
 *     <li>the global context of the pipeline.
 * </ol>
 */
trait DeploymentDetailsAware {

  void withImageFromPrecedingStage(
    Stage stage,
    String targetRegion,
    Closure callback) {
    Stage previousStage = getPreviousStageWithImage(stage, targetRegion)
    def result = [:]
    if (previousStage) {
      if (previousStage.context.containsKey("amiDetails")) {
        result.amiName = previousStage.context.amiDetails.ami
        result.imageId = previousStage.context.amiDetails.imageId
      } else {
        result.amiName = previousStage.context.ami
        result.imageId = previousStage.context.imageId
      }
      callback(result)
    }
  }

  Stage getPreviousStageWithImage(Stage stage, String targetRegion) {
    getAncestors(stage).find {
      (it.context.containsKey("ami") || it.context.containsKey("amiDetails")) && it.context.region == targetRegion
    }
  }

  List<Stage> getAncestors(Stage stage) {
    if (stage.requisiteStageRefIds) {
      def previousStages = stage.execution.stages.findAll {
        it.refId in stage.requisiteStageRefIds
      }
      def syntheticStages = stage.execution.stages.findAll {
        it.parentStageId in previousStages*.id
      }
      return (previousStages + syntheticStages) + previousStages.collect { getAncestors(it) }.flatten()
    } else if (stage.parentStageId) {
      def parent = stage.execution.stages.find { it.id == stage.parentStageId }
      return ([parent] + getAncestors(parent)).flatten()
    } else {
      return []
    }
  }

  void withImageFromDeploymentDetails(
    Stage stage,
    String targetRegion,
    Closure callback) {
    def result = [:]
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>
    if (deploymentDetails) {
      result.amiName = deploymentDetails.find { it.region == targetRegion }?.ami
      // docker image ids are not region or cloud provider specific so no need to filter by region
      result.imageId = deploymentDetails.first().imageId
      callback(result)
    }
  }

}
