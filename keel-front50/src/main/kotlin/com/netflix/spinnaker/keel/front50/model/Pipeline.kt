package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.Instant

/**
 * A Spinnaker pipeline.
 */
data class Pipeline(
  val name: String,
  val id: String,
  val application: String,
  val disabled: Boolean = false,
  val fromTemplate: Boolean = false,
  val triggers: List<Trigger> = emptyList(),
  @JsonAlias("stages")
  private val _stages: List<Stage> = emptyList(),
  @JsonAlias("updateTs")
  private val _updateTs: Long? = null,
  val lastModifiedBy: String? = null
) {
  /**
   * The pipeline stages, in the right order of dependency between them.
   */
  val stages: List<Stage>
    get() = _stages.sortedBy { if (it.requisiteStageRefIds.isEmpty()) "" else it.requisiteStageRefIds.first() }

  /**
   * List of the pipeline stage types ordered by stage dependencies.
   */
  val shape: List<String>
    get() = stages.map { stage -> stage.type }

  val updateTs: Instant?
    get() = _updateTs?.let { Instant.ofEpochMilli(it) }

  val hasParallelStages: Boolean
    get() = stages.any { it.requisiteStageRefIds.size > 1 }

  fun findUpstreamBake(deployStage: DeployStage): BakeStage? {
    val i = stages.indexOf(deployStage)
    return stages.subList(0, i).filterIsInstance<BakeStage>().lastOrNull()
  }

  fun findDownstreamDeploys(stage: Stage): List<DeployStage> {
    val i = stages.indexOf(stage)
    return stages.slice(i until stages.size).filterIsInstance<DeployStage>()
  }

  fun findDeployForCluster(findImageStage: FindImageStage) =
    stages
      .filterIsInstance<DeployStage>()
      .find { deploy ->
        deploy.clusters.any { cluster ->
          findImageStage.cloudProvider == cluster.provider &&
            findImageStage.cluster == cluster.name &&
            findImageStage.credentials == cluster.account &&
            cluster.region in findImageStage.regions
        }
      }

  fun hasManualJudgment(deployStage: DeployStage) =
    try {
      stages[stages.indexOf(deployStage) - 1] is ManualJudgmentStage
    } catch (e: IndexOutOfBoundsException) {
      false
    }

  override fun equals(other: Any?) = if (other is Pipeline) {
    other.id == this.id
  } else {
    super.equals(other)
  }

  override fun hashCode() = id.hashCode()
}

/**
 * Searches the list of pipelines for one that contains a deploy stage matching the cluster described in the given
 * [FindImageStage]. Returns a pair of the pipeline and deploy stage, if found.
 */
fun List<Pipeline>.findPipelineWithDeployForCluster(findImageStage: FindImageStage): Pair<Pipeline, DeployStage>? {
  forEach { pipeline ->
    val deploy = pipeline.findDeployForCluster(findImageStage)
    if (deploy != null) {
      return pipeline to deploy
    }
  }
  return null
}
