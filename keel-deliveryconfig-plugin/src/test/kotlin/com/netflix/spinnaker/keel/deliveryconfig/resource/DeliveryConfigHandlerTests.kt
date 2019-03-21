package com.netflix.spinnaker.keel.deliveryconfig.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.annealing.ResourcePersister
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.deliveryconfig.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.springframework.beans.factory.ObjectFactory
import retrofit2.HttpException
import retrofit2.Response.error

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
  private val objectMapper = ObjectMapper().registerKotlinModule()
  private val resourcePersister = mockk<ResourcePersister>()
  private val resourceRepository = mockk<ResourceRepository>()

  private val RETROFIT_NOT_FOUND = HttpException(
    error<Any>(404, ResponseBody.create(MediaType.parse("application/json"), ""))
  )

  val normalizers = emptyList<ResourceNormalizer<DeliveryConfig>>()

  fun tests() = rootContext<DeliveryConfigHandler> {
    fixture {
      DeliveryConfigHandler(objectMapper, normalizers, ObjectFactory { resourcePersister }, ObjectFactory { resourceRepository })
    }

    //TODO(cfieber) - some tests would be good
  }
}
