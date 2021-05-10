package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.Instant

/**
 * A Spinnaker pipeline.
 */
class Pipeline(
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
  val stages: List<Stage>
    get() = _stages.sortedBy { if (it.requisiteStageRefIds.isEmpty()) "" else it.requisiteStageRefIds.first() }

  val updateTs: Instant?
    get() = _updateTs?.let { Instant.ofEpochMilli(it) }

  val hasParallelStages: Boolean
    get() = stages.any { it.requisiteStageRefIds.size > 1 }

  override fun equals(other: Any?) = if (other is Pipeline) {
    other.id == this.id
  } else {
    super.equals(other)
  }

  override fun hashCode() = id.hashCode()
}
