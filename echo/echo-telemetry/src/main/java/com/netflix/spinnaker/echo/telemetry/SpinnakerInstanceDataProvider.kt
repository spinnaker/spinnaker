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
import com.netflix.spinnaker.echo.config.TelemetryConfig.TelemetryConfigProps
import com.netflix.spinnaker.kork.proto.stats.Application
import com.netflix.spinnaker.kork.proto.stats.DeploymentMethod
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import com.netflix.spinnaker.kork.proto.stats.SpinnakerInstance
import com.netflix.spinnaker.kork.version.ServiceVersion
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(value = ["stats.enabled"], matchIfMissing = true)
class SpinnakerInstanceDataProvider(
  private val config: TelemetryConfigProps,
  private val instanceIdSupplier: InstanceIdSupplier,
  private val serviceVersion: ServiceVersion
) : TelemetryEventDataProvider {
  override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {

    // TelemetryEventListener should ensure this is set
    val applicationId: String = echoEvent.details?.application
      ?: throw IllegalStateException("application not set")

    val instanceId: String = instanceIdSupplier.uniqueId
    val hashedInstanceId: String = hash(instanceId)

    // We want to ensure it's really hard to guess the application name. Using the instance ID (a
    // ULID) provides a good level of randomness as a salt, and is not easily guessable.
    val hashedApplicationId: String = hash(applicationId, instanceId)

    // `stats.spinnaker-version` is not set by the Halyard-free deployment pipeline, so fall back
    // to the version Gradle stamped into echo's jar manifest (resolved via kork's ServiceVersion,
    // the same mechanism used for plugin compatibility checks) unless it's been explicitly
    // overridden in config.
    val version = if (config.spinnakerVersion != TelemetryConfigProps.DEFAULT_SPINNAKER_VERSION) {
      config.spinnakerVersion
    } else {
      serviceVersion.resolve()
    }

    val application = Application.newBuilder().setId(hashedApplicationId).build()
    val spinnakerInstance: SpinnakerInstance = SpinnakerInstance.newBuilder()
      .setId(hashedInstanceId)
      .setVersion(version)
      .setDeploymentMethod(config.deploymentMethod.toProto())
      .build()

    val updatedEvent = statsEvent.toBuilder()
    updatedEvent.spinnakerInstanceBuilder.mergeFrom(spinnakerInstance)
    updatedEvent.applicationBuilder.mergeFrom(application)
    return updatedEvent.build()
  }

  private fun TelemetryConfigProps.DeploymentMethod.toProto(): DeploymentMethod {
    val deploymentType = type
    if (deploymentType == null || version == null) {
      return DeploymentMethod.getDefaultInstance()
    }
    return DeploymentMethod.newBuilder()
      .setType(getProtoDeploymentType(deploymentType))
      .setVersion(version)
      .build()
  }

  private fun getProtoDeploymentType(type: String): DeploymentMethod.Type =
    DeploymentMethod.Type.valueOf(
      DeploymentMethod.Type.getDescriptor().findMatchingValue(type)
    )
}
