package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedResource

fun deliveryArtifact(
  name: String = "fnord",
  configName: String = "myconfig",
): DeliveryArtifact {
  return DebianArtifact(
    name = name,
    deliveryConfigName = configName,
    vmOptions = VirtualMachineOptions(
      baseOs = "bionic",
      regions = setOf("us-west-2")
    )
  )
}

fun submittedDeliveryConfig(
  resource: SubmittedResource<*> = submittedResource(),
  env: SubmittedEnvironment = SubmittedEnvironment("test", setOf(resource)),
  application: String = "fnord",
  configName: String = "myconfig",
  artifact: DeliveryArtifact = deliveryArtifact(configName = configName),
  deliveryConfig: SubmittedDeliveryConfig = SubmittedDeliveryConfig(
    application = application,
    name = configName,
    serviceAccount = "keel@keel.io",
    artifacts = setOf(artifact),
    environments = setOf(env)
  )
): SubmittedDeliveryConfig {
  return deliveryConfig
}
