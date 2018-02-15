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
import com.netflix.spinnaker.orca.kayenta.Thresholds
import java.time.Duration
import java.time.Instant

data class KayentaCanaryContext(
  val metricsAccountName: String? = null,
  val storageAccountName: String? = null,
  val canaryConfigId: String,
  val scopes: List<CanaryConfigScope> = emptyList(),
  val scoreThresholds: Thresholds,
  val startTime: Instant?,
  val endTime: Instant?,
  val step: Duration = Duration.ofSeconds(60),
  private val lifetimeHours: Int? = null,
  private val beginCanaryAnalysisAfterMins: Int = 0,
  private val lookbackMins: Int = 0,
  private val canaryAnalysisIntervalMins: Int? = null
) {
  @JsonIgnore
  val lifetime = if (lifetimeHours == null) null else Duration.ofHours(lifetimeHours.toLong())

  @JsonIgnore
  val beginCanaryAnalysisAfter = Duration.ofMinutes(beginCanaryAnalysisAfterMins.toLong())

  @JsonIgnore
  val lookback = Duration.ofMinutes(lookbackMins.toLong())

  @JsonIgnore
  val canaryAnalysisInterval = if (canaryAnalysisIntervalMins == null) null else Duration.ofMinutes(canaryAnalysisIntervalMins.toLong())
}

data class CanaryConfigScope(
  val scopeName: String = "default",
  val controlScope: String,
  val controlRegion: String?,
  val experimentScope: String,
  val experimentRegion: String?,
  val extendedScopeParams: Map<String, String> = emptyMap()
)
