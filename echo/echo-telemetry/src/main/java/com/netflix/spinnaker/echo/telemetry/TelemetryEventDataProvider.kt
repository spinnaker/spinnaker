/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.echo.telemetry

import com.google.common.hash.Hashing
import com.netflix.spinnaker.echo.api.events.Event as EchoEvent
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import java.nio.charset.StandardCharsets

/**
 * A hook to add data to the community statistics.
 *
 * The ordering in which these are run is determined by Spring.
 */
interface TelemetryEventDataProvider {

  /**
   * Returns a modified [StatsEvent].
   *
   * The only guarantees about [echoEvent] are:
   * * [details][EchoEvent.details] is not-null and contains a non-empty
   *   [type][com.netflix.spinnaker.echo.api.events.Metadata.type] and
   *   [application][com.netflix.spinnaker.echo.api.events.Metadata.application]
   * * [content][EchoEvent.content] is non-null (but might be empty)
   */
  fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent

  fun hash(clearText: String, salt: String = "") =
    Hashing.sha256().hashString(clearText + salt, StandardCharsets.UTF_8).toString()
}
