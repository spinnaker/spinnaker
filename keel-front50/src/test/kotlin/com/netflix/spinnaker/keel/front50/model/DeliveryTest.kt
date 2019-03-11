package com.netflix.spinnaker.keel.front50.model

import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.retrofit.model.ModelParsingTestSupport
import kotlinx.coroutines.Deferred


object DeliveryTest : ModelParsingTestSupport<Front50Service, Delivery>(Front50Service::class.java) {

  override val json = javaClass.getResource("/delivery.json")

  override val call: Front50Service.() -> Deferred<Delivery?> = {
    deliveryById("foo")
  }

  override val expected = Delivery(
    id = "foo",
    application = "bar",
    createTs = 12345,
    updateTs = 12345,
    lastModifiedBy = "anonymous",
    deliveryArtifacts = listOf(mapOf("id" to "artifact1")),
    deliveryEnvironments = listOf(mapOf("id" to "environment1")),
    details = mutableMapOf("otherAttribute1" to "bloop", "otherAttribute2" to "blerp"))
}
