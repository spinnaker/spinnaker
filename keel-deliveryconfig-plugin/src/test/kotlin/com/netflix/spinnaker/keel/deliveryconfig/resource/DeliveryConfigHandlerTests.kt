package com.netflix.spinnaker.keel.deliveryconfig.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.deliveryconfig.DeliveryConfig
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Delivery
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response.error
import strikt.api.expectThat
import strikt.assertions.isNull

internal class DeliveryConfigHandlerTests : JUnit5Minutests {

  private val spec = DeliveryConfig("foo", "bar")
  private val idGenerator = ULID()
  private val resource = Resource(
    SPINNAKER_API_V1,
    "deliveryconfig",
    ResourceMetadata(
      name = ResourceName("foo"),
      uid = idGenerator.nextValue(),
      resourceVersion = 1234L
    ),
    spec
  )
  private val front50Service = mockk<Front50Service>()
  private val objectMapper = ObjectMapper().registerKotlinModule()
  private val RETROFIT_NOT_FOUND = HttpException(
    error<Any>(404, ResponseBody.create(MediaType.parse("application/json"), ""))
  )

  val normalizers = emptyList<ResourceNormalizer<DeliveryConfig>>()

  fun tests() = rootContext<DeliveryConfigHandler> {
    fixture {
      DeliveryConfigHandler(front50Service, objectMapper, normalizers)
    }

    context("the deliveryconfig does not exist") {
      before {
        every { front50Service.deliveryById(any()) } throws RETROFIT_NOT_FOUND
        every { front50Service.upsertDelivery("foo", Delivery(spec.name, spec.application)) } returns CompletableDeferred(Delivery(spec.name, spec.application))
      }

      test("the current model is null") {
        expectThat(current(resource)).isNull()
      }

      test("upserting a deliveryconfig persists to front50") {
        upsert(resource)
        verify { front50Service.upsertDelivery(spec.name, Delivery(spec.name, spec.application)) }
      }
    }
  }
}
