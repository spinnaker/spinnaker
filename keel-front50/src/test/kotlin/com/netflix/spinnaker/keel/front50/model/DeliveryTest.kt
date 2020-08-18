package com.netflix.spinnaker.keel.front50.model

import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.retrofit.model.ModelParsingTestSupport

object DeliveryTest : ModelParsingTestSupport<Front50Service, Delivery>(Front50Service::class.java) {

  override val json =
    """
    |{
    |  "id": "foo",
    |  "application": "bar",
    |  "updateTs": 12345,
    |  "createTs": 12345,
    |  "lastModifiedBy": "anonymous",
    |  "deliveryArtifacts": [
    |    {
    |      "id": "artifact1"
    |    }
    |  ],
    |  "deliveryEnvironments": [
    |    {
    |      "id": "environment1"
    |    }
    |  ],
    |  "otherAttribute1": "bloop",
    |  "otherAttribute2": "blerp"
    |}
  """.trimMargin()

  override suspend fun Front50Service.call(): Delivery? =
    deliveryById("foo")

  override val expected = Delivery(
    id = "foo",
    application = "bar",
    createTs = 12345,
    updateTs = 12345,
    lastModifiedBy = "anonymous",
    deliveryArtifacts = listOf(mapOf("id" to "artifact1")),
    deliveryEnvironments = listOf(mapOf("id" to "environment1")),
    details = mutableMapOf("otherAttribute1" to "bloop", "otherAttribute2" to "blerp")
  )
}
