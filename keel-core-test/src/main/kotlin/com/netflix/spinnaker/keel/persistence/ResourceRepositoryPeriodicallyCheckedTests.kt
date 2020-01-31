package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource

abstract class ResourceRepositoryPeriodicallyCheckedTests<S : ResourceRepository> :
  PeriodicallyCheckedRepositoryTests<Resource<out ResourceSpec>, S>() {

  override val descriptor = "resource"

  override val createAndStore: Fixture<Resource<out ResourceSpec>, S>.(Int) -> Collection<Resource<out ResourceSpec>> =
    { count ->
      (1..count)
        .map { i ->
          resource(id = "fnord-$i").also(subject::store)
        }
    }

  override val updateOne: Fixture<Resource<out ResourceSpec>, S>.() -> Resource<DummyResourceSpec> = {
    subject
      .get<DummyResourceSpec>("test:whatever:fnord-1")
      .let {
        it.copy(spec = it.spec.copy(data = randomString()))
      }
      .also(subject::store)
  }
}
