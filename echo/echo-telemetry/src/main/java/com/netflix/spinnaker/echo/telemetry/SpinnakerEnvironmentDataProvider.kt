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

import com.netflix.spinnaker.echo.api.events.Event as EchoEvent
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import com.netflix.spinnaker.kork.proto.stats.SpinnakerInstance
import com.netflix.spinnaker.kork.proto.stats.SpinnakerInstance.DeployedArtifacts
import com.netflix.spinnaker.kork.proto.stats.SpinnakerInstance.Environment
import java.io.IOException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Sets the [SpinnakerInstance.Environment] and [SpinnakerInstance.DeployedArtifacts] fields of the
 * stats proto.
 *
 * This is mostly in its own [TelemetryEventDataProvider] because it's not really testable.
 */
@Component
@ConditionalOnProperty(value = ["stats.enabled"], matchIfMissing = true)
class SpinnakerEnvironmentDataProvider : TelemetryEventDataProvider {

  private val environment by lazy { computeDeploymentEnvironment() }

  override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
    val newEvent = statsEvent.toBuilder()
    newEvent.spinnakerInstanceBuilder.mergeFrom(environment)
    return newEvent.build()
  }

  private fun computeDeploymentEnvironment(): SpinnakerInstance {
    val result = SpinnakerInstance.newBuilder()
    if (System.getenv("KUBERNETES_PORT") != null) {
      result.apply {
        environment = Environment.KUBERNETES
        deployedArtifacts = DeployedArtifacts.DOCKER_CONTAINERS
      }
    } else if (debianPackageIsInstalled()) {
      result.apply {
        environment = Environment.ENVIRONMENT_UNKNOWN
        deployedArtifacts = DeployedArtifacts.DEBIAN_PACKAGES
      }
    } else {
      result.apply {
        environment = Environment.ENVIRONMENT_UNKNOWN
        deployedArtifacts = DeployedArtifacts.DEPLOYED_ARTIFACTS_UNKNOWN
      }
    }
    return result.build()
  }

  private fun debianPackageIsInstalled(): Boolean {
    try {
      val exitCode = ProcessBuilder()
        .command("/usr/bin/dpkg-query", "-s", "spinnaker-echo")
        .start()
        .waitFor()
      return exitCode == 0
    } catch (e: IOException) {
      return false
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      return false
    }
  }
}
