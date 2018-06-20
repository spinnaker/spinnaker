/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel

import com.google.common.annotations.VisibleForTesting
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.ApplicationAssetGuardProperties
import com.netflix.spinnaker.config.KindAssetGuardProperties
import com.netflix.spinnaker.keel.event.AssetAwareEvent
import com.netflix.spinnaker.keel.event.BeforeAssetConvergeEvent
import com.netflix.spinnaker.keel.event.BeforeAssetDeleteEvent
import com.netflix.spinnaker.keel.event.BeforeAssetUpsertEvent
import com.netflix.spinnaker.keel.exceptions.GuardConditionFailed
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener

open class WhitelistingAssetGuardProperties {
  var enabled: Boolean = true
  var whitelist: MutableList<String> = mutableListOf()
}

// TODO rz - account guard
abstract class WhitelistingAssetGuard(
  private val registry: Registry,
  private val properties: WhitelistingAssetGuardProperties
) {

  private val log = LoggerFactory.getLogger(javaClass)

  private val whitelist: List<String> = properties.whitelist.map { it.trim().toLowerCase() }
  private val failedId = registry.createId("guard.whitelist.failed", "guard", javaClass.simpleName)

  init {
    if (properties.enabled) {
      log.info("Guard enabled: ${javaClass.simpleName}")
    } else {
      log.warn("Guard disabled: ${javaClass.simpleName}")
    }
  }

  protected abstract val supportedEvents: List<Class<out AssetAwareEvent>>

  protected abstract fun extractValue(event: AssetAwareEvent): String?

  @EventListener(AssetAwareEvent::class)
  fun onAssetAwareEvent(event: AssetAwareEvent) {
    if (properties.enabled && matchesEventTypes(event)) {
      val value = extractValue(event)?.trim()?.toLowerCase()
      if (value != null && !whitelist.contains(value)) {
        registry.counter(failedId.withTags(mapOf(
          "value" to value,
          "event" to event.toString()
        ))).increment()
        throw GuardConditionFailed(javaClass.simpleName, "Whitelist does not contain '$value' in $event")
      }
    }
  }

  @VisibleForTesting
  internal fun matchesEventTypes(event: AssetAwareEvent) =
    supportedEvents.isEmpty() || supportedEvents.any { it.isInstance(event) }
}

class ApplicationAssetGuard(
  registry: Registry,
  applicationGuardProperties: ApplicationAssetGuardProperties
) : WhitelistingAssetGuard(registry, applicationGuardProperties) {

  override val supportedEvents = listOf(
    BeforeAssetUpsertEvent::class.java,
    BeforeAssetDeleteEvent::class.java,
    BeforeAssetConvergeEvent::class.java
  )

  override fun extractValue(event: AssetAwareEvent): String? {
    if (event.asset.spec is ApplicationAwareAssetSpec) {
      return (event.asset.spec as ApplicationAwareAssetSpec).application
    }
    return null
  }
}

class KindAssetGuard(
  registry: Registry,
  kindGuardProperties: KindAssetGuardProperties
) : WhitelistingAssetGuard(registry, kindGuardProperties) {

  override val supportedEvents = listOf(
    BeforeAssetUpsertEvent::class.java,
    BeforeAssetDeleteEvent::class.java,
    BeforeAssetConvergeEvent::class.java
  )

  override fun extractValue(event: AssetAwareEvent) = event.asset.kind
}
