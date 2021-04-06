package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resource

abstract class ResourceRepositoryPeriodicallyCheckedTests<S : ResourceRepository> :
  PeriodicallyCheckedRepositoryTests<Resource<ResourceSpec>, S>() {

  override val descriptor = "resource"

  abstract val storeDeliveryConfig: (DeliveryConfig) -> Unit

  override val createAndStore: Fixture<Resource<ResourceSpec>, S>.(Int) -> Collection<Resource<ResourceSpec>> =
    { count ->
      (1..count).mapTo(mutableSetOf()) { i ->
        resource(id = "fnord-$i").also {
          subject.store(it)
        }
      }
        .also { resources ->
          storeDeliveryConfig(deliveryConfig(resources = resources))
        }
    }

  override val updateOne: Fixture<Resource<ResourceSpec>, S>.() -> Resource<DummyResourceSpec> = {
    subject
      .get<DummyResourceSpec>("test:whatever:fnord-1")
      .let {
        it.copy(spec = it.spec.copy(data = randomString()))
      }
      .also(subject::store)
  }
}
