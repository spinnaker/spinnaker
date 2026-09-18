package com.netflix.spinnaker.keel.bakery.telemetry

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import com.netflix.spinnaker.keel.bakery.artifact.BakeLaunched
import com.netflix.spinnaker.keel.bakery.artifact.ImageRegionMismatchDetected
import com.netflix.spinnaker.keel.bakery.artifact.RecurrentBakeDetected
import com.netflix.spinnaker.keel.bakery.constraint.MissingRegionsDetected
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BakeryTelemetryListener(private val meterRegistry: MeterRegistry) {

  @EventListener(ImageRegionMismatchDetected::class)
  fun onImageRegionMismatchDetected(event: ImageRegionMismatchDetected) {
    meterRegistry.counter(
      IMAGE_REGION_MISMATCH_DETECTED_ID,
      listOf(
        Tag.of("appVersion", event.appVersion),
        Tag.of("baseAmiName", event.baseAmiName),
        Tag.of("found", event.foundRegions.joinToString()),
        Tag.of("desired", event.desiredRegions.joinToString())
      )
    )
      .runCatching { increment() }
      .onFailure {
        log.error("Exception incrementing {} counter: {}", IMAGE_REGION_MISMATCH_DETECTED_ID, it.message)
      }
  }

  @EventListener(BakeLaunched::class)
  fun onBakeLaunched(event: BakeLaunched) {
    meterRegistry.counter(
      BAKE_LAUNCHED_ID,
      listOf(
        Tag.of("appVersion", event.appVersion)
      )
    )
      .runCatching { increment() }
      .onFailure {
        log.error("Exception incrementing {} counter: {}", BAKE_LAUNCHED_ID, it.message)
      }
  }

  @EventListener(RecurrentBakeDetected::class)
  fun onRecurrentBakeDetected(event: RecurrentBakeDetected) {
    meterRegistry.counter(
      RECURRENT_BAKE_DETECTED_ID,
      listOf(
        Tag.of("versions", "${event.appVersion}+${event.baseAmiVersion}")
      )
    )
  }

  @EventListener(MissingRegionsDetected::class)
  fun onMissingRegionsDetected(event: MissingRegionsDetected) {
    meterRegistry.counter(
      MISSING_REGIONS_DETECTED,
      listOf(
        Tag.of("versions", event.version)
      )
    )
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private const val IMAGE_REGION_MISMATCH_DETECTED_ID = "keel.bakery.image.region.mismatch"
    private const val BAKE_LAUNCHED_ID = "keel.bakery.bake.launched"
    private const val RECURRENT_BAKE_DETECTED_ID = "keel.bakery.recurrent.bake.detected"
    private const val MISSING_REGIONS_DETECTED = "keel.bakery.missing.regions.detected"
  }
}
