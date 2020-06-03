/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.keel.api.constraints

import com.netflix.spinnaker.keel.api.UID
import java.time.Duration
import java.time.Instant

/**
 * TODO: Docs
 */
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
  var uid: UID? = null
) {
  fun passed() = status.passes()

  fun failed() = status.failed()

  fun timedOut(timeout: Duration, now: Instant) =
    createdAt.plus(timeout).isBefore(now)
}
