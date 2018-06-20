/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.event

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.netflix.spinnaker.keel.Asset
import com.netflix.spinnaker.keel.AssetSpec
import org.springframework.context.ApplicationEvent

enum class EventKind(val kind: String) {
  BEFORE_ASSET_UPSERT("beforeAssetUpsert"),
  AFTER_ASSET_UPSERT("afterAssetUpsert"),
  BEFORE_ASSET_DELETE("beforeAssetDelete"),
  AFTER_ASSET_DELETE("afterAssetDelete"),
  BEFORE_ASSET_DRYRUN("beforeAssetDryRun"),
  BEFORE_ASSET_SCHEDULE("beforeAssetSchedule"),
  BEFORE_ASSET_CONVERGE("beforeAssetConverge"),
  ASSET_CONVERGE_TIMEOUT("assetConvergeTimeout"),
  ASSET_CONVERGE_NOT_FOUND("assetConvergeNotFound"),
  ASSET_CONVERGE_SUCCESS("assetConvergeSuccess"),
  ASSET_CONVERGE_FAILURE("assetConvergeFailure");

  companion object {
    @JvmStatic @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromValue(value: String): EventKind {
      return EventKind.values()
        .find { it.kind == value || it.name == value }
        ?: throw IllegalArgumentException("$value is not a valid EventKind")
    }
  }

  @JsonValue fun toValue() = kind
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
@JsonSubTypes(
  Type(AfterAssetUpsertEvent::class),
  Type(AfterAssetDeleteEvent::class),
  Type(BeforeAssetScheduleEvent::class),
  Type(BeforeAssetConvergeEvent::class),
  Type(AssetConvergeTimeoutEvent::class),
  Type(AssetConvergeNotFoundEvent::class),
  Type(AssetConvergeSuccessEvent::class),
  Type(AssetConvergeFailureEvent::class)
)
abstract class KeelEvent : ApplicationEvent("internal") {
  abstract val kind: EventKind
}

abstract class AssetAwareEvent() : KeelEvent() {
  abstract val asset: Asset<AssetSpec>
}

data class BeforeAssetUpsertEvent(
  override val asset: Asset<AssetSpec>
) : AssetAwareEvent() {
  override val kind = EventKind.BEFORE_ASSET_UPSERT
}

data class AfterAssetUpsertEvent(
  override val asset: Asset<AssetSpec>
) : AssetAwareEvent() {
  override val kind = EventKind.AFTER_ASSET_UPSERT
}

data class BeforeAssetDeleteEvent(
  override val asset: Asset<AssetSpec>
) : AssetAwareEvent() {
  override val kind = EventKind.BEFORE_ASSET_DELETE
}

data class AfterAssetDeleteEvent(
  override val asset: Asset<AssetSpec>
) : AssetAwareEvent() {
  override val kind = EventKind.AFTER_ASSET_DELETE
}

data class BeforeAssetDryRunEvent(
  override val asset: Asset<AssetSpec>
) : AssetAwareEvent() {
  override val kind = EventKind.BEFORE_ASSET_DRYRUN
}

data class BeforeAssetScheduleEvent(
  override val asset: Asset<AssetSpec>
) : AssetAwareEvent() {
  override val kind = EventKind.BEFORE_ASSET_SCHEDULE
}

data class BeforeAssetConvergeEvent(
  override val asset: Asset<AssetSpec>
) : AssetAwareEvent() {
  override val kind = EventKind.BEFORE_ASSET_CONVERGE
}

data class AssetConvergeTimeoutEvent(
  override val asset: Asset<AssetSpec>
) : AssetAwareEvent() {
  override val kind = EventKind.ASSET_CONVERGE_TIMEOUT
}

data class AssetConvergeNotFoundEvent(
  val assetId: String
) : KeelEvent() {
  override val kind = EventKind.ASSET_CONVERGE_NOT_FOUND
}

data class AssetConvergeSuccessEvent(
  override val asset: Asset<AssetSpec>,
  val orchestrations: List<String>
) : AssetAwareEvent() {
  override val kind = EventKind.ASSET_CONVERGE_SUCCESS
}

data class AssetConvergeFailureEvent(
  override val asset: Asset<AssetSpec>,
  val reason: String,
  val cause: Throwable?
) : AssetAwareEvent() {
  override val kind = EventKind.ASSET_CONVERGE_FAILURE
}
