package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.BaseLabel
import com.netflix.spinnaker.keel.api.artifacts.BaseLabel.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.StoreType
import com.netflix.spinnaker.keel.api.artifacts.StoreType.EBS
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact

/**
 * A stage in a Spinnaker [Pipeline].
 */
@JsonTypeInfo(
  use = Id.NAME,
  include = As.EXISTING_PROPERTY,
  property = "type",
  visible = true, // the default is false and hides the property from the deserializer!
  defaultImpl = GenericStage::class
)
@JsonSubTypes(
  Type(value = BakeStage::class, name = "bake"),
  Type(value = DeployStage::class, name = "deploy"),
  Type(value = FindImageStage::class, name = "findImage"),
  Type(value = FindImageFromTagsStage::class, name = "findImageFromTags"),
  Type(value = ManualJudgmentStage::class, name = "manualJudgment")
)
abstract class Stage {
  abstract val type: String
  abstract val name: String
  abstract val refId: String
  open val requisiteStageRefIds: List<String> = emptyList()

  @get:JsonAnyGetter
  val details: MutableMap<String, Any> = mutableMapOf()

  @JsonAnySetter
  fun setAttribute(key: String, value: Any) {
    details[key] = value
  }
}

data class GenericStage(
  override val name: String,
  override val type: String,
  override val refId: String,
  override val requisiteStageRefIds: List<String> = emptyList()
) : Stage()

data class BakeStage(
  override val name: String,
  override val type: String,
  override val refId: String,
  override val requisiteStageRefIds: List<String> = emptyList(),
  val `package`: String,
  val baseLabel: BaseLabel = RELEASE,
  val baseOs: String,
  val regions: Set<String>,
  val storeType: StoreType = EBS,
  val vmType: String = "hvm",
  val cloudProviderType: String = "aws"
) : Stage() {

  val artifact: DebianArtifact
    get() = DebianArtifact(
      name = `package`,
      vmOptions = VirtualMachineOptions(
        baseLabel = baseLabel,
        baseOs = baseOs,
        regions = regions,
        storeType = storeType
      ),
      statuses = try {
        setOf(ArtifactStatus.valueOf(baseLabel.name))
      } catch (e: IllegalArgumentException) {
        emptySet()
      }
    )
}

data class DeployStage(
  override val name: String,
  override val type: String,
  override val refId: String,
  override val requisiteStageRefIds: List<String> = emptyList(),
  val clusters: Set<Cluster>
) : Stage()

enum class SelectionStrategy {
  LARGEST,
  NEWEST,
  OLDEST,
  FAIL
}

data class FindImageStage(
  override val name: String,
  override val type: String,
  override val refId: String,
  override val requisiteStageRefIds: List<String> = emptyList(),
  val cluster: String,
  val credentials: String, // account
  val onlyEnabled: Boolean,
  val selectionStrategy: SelectionStrategy,
  val cloudProvider: String,
  val regions: Set<String>,
  val cloudProviderType: String = cloudProvider
) : Stage()

data class FindImageFromTagsStage(
  override val name: String,
  override val type: String,
  override val refId: String,
  override val requisiteStageRefIds: List<String> = emptyList(),
) : Stage()

data class ManualJudgmentStage(
  override val name: String,
  override val type: String,
  override val refId: String,
  override val requisiteStageRefIds: List<String> = emptyList(),
) : Stage()
