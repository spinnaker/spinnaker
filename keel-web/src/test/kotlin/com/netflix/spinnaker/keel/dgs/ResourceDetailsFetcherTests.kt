package com.netflix.spinnaker.keel.dgs

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.spinnaker.keel.graphql.types.MdResource
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.test.deliveryConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ResourceDetailsFetcherTests {
  private val applicationFetcherSupport: ApplicationFetcherSupport = mockk()
  private val yamlMapper = configuredYamlMapper()
  private val resourceDetailsFetcher = ResourceDetailsFetcher(applicationFetcherSupport, yamlMapper)
  private val dfe: DgsDataFetchingEnvironment = mockk()
  private val deliveryConfig = deliveryConfig()
  private val environment = deliveryConfig.environments.first()
  private val resource = environment.resources.first()

  @BeforeEach
  fun setup() {
    every {
      dfe.getSource<MdResource>()
    } returns resource.toDgs(deliveryConfig, environment.name)

    every {
      applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    } returns deliveryConfig
  }

  @Test
  fun `returns the raw resource definition as YAML`() {
    val yaml = resourceDetailsFetcher.rawDefinition(dfe)

    expectThat(yaml)
      .isEqualTo(yamlMapper.writeValueAsString(resource))
  }
}