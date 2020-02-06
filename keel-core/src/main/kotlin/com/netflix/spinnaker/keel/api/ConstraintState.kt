package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Duration
import java.time.Instant

data class ConstraintState(
  val deliveryConfigName: String,
  val environmentName: String,
  val artifactVersion: String,
  val type: String,
  val status: ConstraintStatus,
  val createdAt: Instant = Instant.now(),
  val judgedBy: String? = null,
  val judgedAt: Instant? = null,
  val comment: String? = null,
  val attributes: ConstraintStateAttributes? = null,
  @JsonIgnore
  var uid: UID? = null
) {
  fun passed() = status.passes()

  fun failed() = status.failed()

  fun timedOut(timeout: Duration, now: Instant) =
    createdAt.plus(timeout).isBefore(now)
}

enum class ConstraintStatus(private val passed: Boolean, private val failed: Boolean) {
  PENDING(false, false),
  PASS(true, false),
  FAIL(false, true),
  OVERRIDE_PASS(true, false),
  OVERRIDE_FAIL(false, true);

  fun passes() = passed
  fun failed() = failed
}

data class UpdatedConstraintStatus(
  val type: String,
  val artifactVersion: String,
  val status: ConstraintStatus,
  val comment: String?
)

@JsonTypeInfo(
  include = JsonTypeInfo.As.EXISTING_PROPERTY,
  use = JsonTypeInfo.Id.NAME,
  property = "type")
@JsonSubTypes(
  Type(value = PipelineConstraintStateAttributes::class, name = "pipeline"),
  Type(value = CanaryConstraintAttributes::class, name = "canary")
)
abstract class ConstraintStateAttributes(val type: String)

data class PipelineConstraintStateAttributes(
  val executionId: String? = null,
  val attempt: Int,
  val latestAttempt: Instant,
  val lastExecutionStatus: String? = null
) : ConstraintStateAttributes("pipeline")

data class CanaryConstraintAttributes(
  val executions: Set<RegionalExecutionId> = emptySet(),
  val startAttempt: Int = 0,
  val status: Set<CanaryStatus> = emptySet()
) : ConstraintStateAttributes("canary")

data class RegionalExecutionId(
  val region: String,
  val executionId: String
)

data class CanaryStatus(
  val executionId: String,
  val region: String,
  val executionStatus: String,
  val scores: List<Double> = emptyList(),
  val scoreMessage: String?
)
