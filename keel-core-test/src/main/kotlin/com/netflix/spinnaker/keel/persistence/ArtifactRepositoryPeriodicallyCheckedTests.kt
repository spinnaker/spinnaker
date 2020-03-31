package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions

abstract class ArtifactRepositoryPeriodicallyCheckedTests<S : ArtifactRepository> :
  PeriodicallyCheckedRepositoryTests<DeliveryArtifact, S>() {

  override val descriptor = "artifact"

  override val createAndStore: Fixture<DeliveryArtifact, S>.(count: Int) -> Collection<DeliveryArtifact> = { count ->
    (1..count)
      .map { i ->
        DebianArtifact(
          name = "artifact-$i",
          deliveryConfigName = "delivery-config-$i",
          reference = "ref-$i",
          vmOptions = VirtualMachineOptions(
            baseOs = "bionic-classic",
            regions = setOf("us-west-2", "us-east-1")
          )
        )
          .also(subject::register)
      }
  }

  override val updateOne: Fixture<DeliveryArtifact, S>.() -> DeliveryArtifact = {
    subject
      .get("artifact-1", ArtifactType.deb, "ref-1", "delivery-config-1")
      .let { it as DebianArtifact }
      .copy(reference = "my-delightful-artifact")
      .also(subject::register)
  }
}
