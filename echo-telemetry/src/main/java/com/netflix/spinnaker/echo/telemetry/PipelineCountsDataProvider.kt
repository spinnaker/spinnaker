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

import com.google.common.base.Suppliers
import com.netflix.spinnaker.echo.api.events.Event as EchoEvent
import com.netflix.spinnaker.echo.services.Front50Service
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import java.util.concurrent.TimeUnit
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("stats.enabled")
class PipelineCountsDataProvider(private val front50: Front50Service) : TelemetryEventDataProvider {

  private val appPipelinesSupplier =
    Suppliers.memoizeWithExpiration({ retrievePipelines() }, 30, TimeUnit.MINUTES)

  override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {

    val newEvent = statsEvent.toBuilder()
    val appPipelines = appPipelinesSupplier.get()
    val appPipelineCount = appPipelines[echoEvent.details.application]
    if (appPipelineCount != null) {
      newEvent.applicationBuilder.pipelineCount = appPipelineCount
    }
    newEvent.spinnakerInstanceBuilder.pipelineCount = appPipelines.values.sum()
    return newEvent.build()
  }

  private fun retrievePipelines(): Map<String, Int> {
    return front50.pipelines
      .filter { it.containsKey("application") }
      .groupBy { it["application"] as String }
      .mapValues { it.value.size }
  }
}
