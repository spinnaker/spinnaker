/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kayenta.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.orca.kayenta.Thresholds
import java.time.Duration
import java.time.Instant

data class KayentaCanaryContext(
  val metricsAccountName: String? = null,
  val configurationAccountName: String? = null,
  val storageAccountName: String? = null,
  val canaryConfigId: String,
  val scopes: List<CanaryConfigScope> = emptyList(),
  val scoreThresholds: Thresholds = Thresholds(pass = 75, marginal = 50),
  @Deprecated("Kept to support pipelines that haven't been updated to use lifetimeDuration")

  @JsonProperty(access = JsonProperty.Access.READ_WRITE)
  private val lifetimeHours: Int? = null,

  @JsonProperty(access = JsonProperty.Access.READ_WRITE)
  private val lifetimeDuration: Duration? = null,

  @JsonProperty(access = JsonProperty.Access.READ_WRITE)
  private val beginCanaryAnalysisAfterMins: Int = 0,

  @JsonProperty(access = JsonProperty.Access.READ_WRITE)
  private val lookbackMins: Int = 0,

  @JsonProperty(access = JsonProperty.Access.READ_WRITE)
  private val canaryAnalysisIntervalMins: Int? = null
) {
  @JsonIgnore
  val lifetime = when {
    lifetimeDuration != null -> lifetimeDuration
    lifetimeHours != null -> Duration.ofHours(lifetimeHours.toLong())
    else -> null
  }

  @JsonIgnore
  val beginCanaryAnalysisAfter = Duration.ofMinutes(beginCanaryAnalysisAfterMins.toLong())

  @JsonIgnore
  val lookback = Duration.ofMinutes(lookbackMins.toLong())

  @JsonIgnore
  val canaryAnalysisInterval = if (canaryAnalysisIntervalMins == null) null else Duration.ofMinutes(canaryAnalysisIntervalMins.toLong())
}

data class CanaryConfigScope(
  val scopeName: String = "default",
  val controlScope: String?,
  val controlLocation: String?,
  val experimentScope: String?,
  val experimentLocation: String?,
  val startTimeIso: String?,
  val endTimeIso: String?,
  val step: Long = 60, // TODO: clarify this is in seconds
  val extendedScopeParams: Map<String, String> = emptyMap()
) {
  // I don't love having these as separate properties but other things in Orca rely
  // on serializing Instant as epoch Millis which is not what Kayenta wants.
  @JsonIgnore
  val startTime = if (startTimeIso == null) null else Instant.parse(startTimeIso)

  @JsonIgnore
  val endTime = if (endTimeIso == null) null else Instant.parse(endTimeIso)
}
