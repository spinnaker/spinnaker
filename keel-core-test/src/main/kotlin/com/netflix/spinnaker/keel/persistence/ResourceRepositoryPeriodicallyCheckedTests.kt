package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID

abstract class ResourceRepositoryPeriodicallyCheckedTests<S : ResourceRepository> :
  PeriodicallyCheckedRepositoryTests<ResourceHeader, S>() {

  override val descriptor = "resource"

  override val createAndStore: Fixture<ResourceHeader, S>.(Int) -> Collection<ResourceHeader> = { count ->
    (1..count)
      .map {
        Resource(
          apiVersion = SPINNAKER_API_V1,
          metadata = mapOf(
            "name" to "ec2:security-group:test:us-west-2:fnord-$it",
            "uid" to randomUID(),
            "serviceAccount" to "keel@spinnaker",
            "application" to "fnord"
          ) + randomData(),
          kind = "security-group",
          spec = randomData()
        )
          .also(subject::store)
      }
      .map(::ResourceHeader)
  }

  override val updateOne: Fixture<ResourceHeader, S>.() -> ResourceHeader = {
    subject.get(
      ResourceName("ec2:security-group:test:us-west-2:fnord-1"),
      Any::class.java
    )
      .copy(spec = randomData())
      .also(subject::store)
      .let(::ResourceHeader)
  }
}
