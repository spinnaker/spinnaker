package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintRepository
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintAttributesType
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.core.api.ZonedDateTimeRange
import com.netflix.spinnaker.keel.core.api.activeWindowOrNull
import com.netflix.spinnaker.keel.core.api.windowRange
import com.netflix.spinnaker.keel.core.api.windowsNumeric
import com.netflix.spinnaker.keel.core.api.zone
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneId

/**
 * An environment promotion constraint to gate promotions to time windows.
 * Multiple windows can be specified.
 *
 * Hours and days parameters support a mix of ranges and comma separated values.
 *
 * Days can be specified using full or short names according to the default JVM locale.
 *
 * A timezone can be set in the constraint and a system-wide default can by set via
 * the dynamic property "default.time-zone" which defaults to UTC.
 *
 * Example env constraint:
 *
 * ```
 * constraints:
 *  - type: allowed-times
 *    windows:
 *      - days: Monday,Tuesday-Friday
 *        hours: 11-16
 *      - days: Wed
 *        hours: 12-14
 *    tz: America/Los_Angeles
 *    maxDeploysPerWindow: 2
 * ```
 */
@Component
class AllowedTimesConstraintEvaluator(
  private val clock: Clock,
  private val dynamicConfigService: DynamicConfigService,
  override val eventPublisher: EventPublisher,
  override val repository: ConstraintRepository,
  private val artifactRepository: ArtifactRepository
) : StatefulConstraintEvaluator<TimeWindowConstraint, AllowedTimesConstraintAttributes> {
  override val attributeType = SupportedConstraintAttributesType<AllowedTimesConstraintAttributes>("allowed-times")

  companion object {
    const val CONSTRAINT_NAME = "allowed-times"
  }

  override val supportedType = SupportedConstraintType<TimeWindowConstraint>("allowed-times")

  /**
   * We want this constraint to be able to flip the status from pass to fail
   */
  override fun shouldAlwaysReevaluate(): Boolean = true

  private fun actualVsMaxDeploys(
    constraint: TimeWindowConstraint,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    windowRange: ZonedDateTimeRange
  ): Pair<Int, Int>? = if (constraint.maxDeploysPerWindow != null) {
    artifactRepository.versionsApprovedBetween(
      deliveryConfig,
      targetEnvironment.name,
      windowRange.start.toInstant(),
      windowRange.endInclusive.toInstant()
    ) to constraint.maxDeploysPerWindow
  } else {
    null
  }

  private fun currentWindowOrNull(constraint: TimeWindowConstraint): ZonedDateTimeRange? {
    val tz = constraint.zone ?: defaultTimeZone()

    val now = clock
      .instant()
      .atZone(tz)

    return constraint.activeWindowOrNull(now)?.windowRange(now)
  }

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: TimeWindowConstraint,
    state: ConstraintState
  ): Boolean {

    if (state.judgedByUser()) {
      // if a user has judged this constraint, always take that judgement.
      if (state.failed()) {
        return false
      } else if (state.passed()) {
        return true
      }
    }

    val windowRange = currentWindowOrNull(constraint)
    val actualVsMaxDeploys = if (windowRange != null && constraint.maxDeploysPerWindow != null) {
      actualVsMaxDeploys(constraint, deliveryConfig, targetEnvironment, windowRange)
    } else {
      null
    }

    val status = when {
      windowRange == null -> FAIL
      actualVsMaxDeploys == null -> PASS
      actualVsMaxDeploys.first < actualVsMaxDeploys.second -> {
        log.debug(
          "{}:{} has only been deployed {} times during the current window ({} to {}), allowing deployment",
          deliveryConfig.name,
          targetEnvironment.name,
          actualVsMaxDeploys.first,
          windowRange.start.toLocalTime(),
          windowRange.endInclusive.toLocalTime()
        )
        PASS
      }
      else -> {
        log.info(
          "{}:{} has already been deployed {} times during the current window ({} to {}), skipping deployment",
          deliveryConfig.name,
          targetEnvironment.name,
          actualVsMaxDeploys.first,
          windowRange.start.toLocalTime(),
          windowRange.endInclusive.toLocalTime()
        )
        FAIL
      }
    }

    if (status != state.status) {
      // change the stored state if the current calculated status is different from what's saved.
      val attributes = AllowedTimesConstraintAttributes(
        allowedTimes = constraint.windowsNumeric,
        timezone = constraint.tz ?: defaultTimeZone().id,
        actualDeploys = actualVsMaxDeploys?.first,
        maxDeploys = actualVsMaxDeploys?.second,
        currentlyPassing = status == PASS
      )
      repository.storeConstraintState(
        state.copy(
          attributes = attributes,
          status = status,
          judgedAt = clock.instant(),
          judgedBy = "Spinnaker"
        )
      )
    } else {
      // update the judged time so it's clear when we last checked
      repository.storeConstraintState(state.copy(judgedAt = clock.instant(), judgedBy = "Spinnaker"))
    }

    return status == PASS
  }

  private fun defaultTimeZone(): ZoneId =
    ZoneId.of(
      dynamicConfigService.getConfig(String::class.java, "default.time-zone", "America/Los_Angeles")
    )

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
