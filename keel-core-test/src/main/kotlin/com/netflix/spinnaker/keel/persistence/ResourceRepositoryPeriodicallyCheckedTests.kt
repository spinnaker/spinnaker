package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource

abstract class ResourceRepositoryPeriodicallyCheckedTests<S : ResourceRepository> :
  PeriodicallyCheckedRepositoryTests<ResourceHeader, S>() {

  override val descriptor = "resource"

  override val createAndStore: Fixture<ResourceHeader, S>.(Int) -> Collection<ResourceHeader> = { count ->
    (1..count)
      .map { i ->
        resource(name = "fnord-$i").also(subject::store)
      }
      .map(::ResourceHeader)
  }

  override val updateOne: Fixture<ResourceHeader, S>.() -> ResourceHeader = {
    subject
      .get<DummyResourceSpec>(ResourceName("test:whatever:fnord-1"))
      .let {
        it.copy(spec = it.spec.copy(data = randomString()))
      }
      .also(subject::store)
      .let(::ResourceHeader)
  }
}
