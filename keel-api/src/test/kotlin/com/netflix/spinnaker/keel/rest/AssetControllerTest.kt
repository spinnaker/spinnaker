package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.NoSuchAssetException
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.redis.spring.EmbeddedRedisConfiguration
import com.netflix.spinnaker.keel.redis.spring.MockEurekaConfig
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.util.*

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfig::class, EmbeddedRedisConfiguration::class],
  properties = [
    "clouddriver.baseUrl=https://localhost:8081",
    "orca.baseUrl=https://localhost:8082"
  ],
  webEnvironment = RANDOM_PORT
)
@AutoConfigureMockMvc
internal class AssetControllerTest {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var assetRepository: AssetRepository

  @Test
  fun `can create a resource as YAML`() {
    val request = post("/assets")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: spinnaker.netflix.com/v1
          |kind: ec2.SecurityGroup
          |metadata:
          |  name: ec2.SecurityGroup:keel:test:us-west-2:keel
          |spec:
          |  account: test
          |  region: us-west-2
          |  name: keel"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isOk)
  }

  @Test
  fun `can create a resource as JSON`() {
    val request = post("/assets")
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      .content(
        """{
          |  "apiVersion": "spinnaker.netflix.com/v1",
          |  "kind": "ec2.SecurityGroup",
          |  "metadata": {
          |    "name": "ec2.SecurityGroup:keel:test:us-west-2:keel"
          |  },
          |  "spec": {
          |    "account": "test",
          |    "region": "us-west-2",
          |    "name": "keel"
          |  }
          |}"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isOk)
  }

  @Test
  fun `can get a resource as YAML`() {
    val asset = Asset(
      apiVersion = SPINNAKER_API_V1,
      kind = "whatever",
      metadata = AssetMetadata(
        name = AssetName("my-resource"),
        uid = UUID.randomUUID(),
        resourceVersion = 1234L
      ),
      spec = "some spec content"
    )
    assetRepository.store(asset)

    val request = get("/assets/${asset.metadata.name}")
      .accept(APPLICATION_YAML)
    val result = mvc
      .perform(request)
      .andExpect(status().isOk)
      .andReturn()
    expectThat(result.response.getHeaderValue(CONTENT_TYPE))
      .isA<String>()
      .get { MediaType.parseMediaType(this) }
      .get { "$type/$subtype" }
      .isEqualTo(APPLICATION_YAML_VALUE)
  }

  @Test
  @Ignore("TODO: Event needs to go via k8s for delete to happen")
  fun `can delete a resource`() {
    val asset = Asset(
      apiVersion = SPINNAKER_API_V1,
      kind = "whatever",
      metadata = AssetMetadata(
        name = AssetName("my-resource"),
        uid = UUID.randomUUID(),
        resourceVersion = 1234L
      ),
      spec = "some spec content"
    )
    assetRepository.store(asset)

    val request = delete("/assets/${asset.metadata.name}")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isOk)

    expectThrows<NoSuchAssetException> {
      assetRepository.get<Any>(asset.metadata.name)
    }
  }

  @Test
  fun `unknown asset name results in a 404`() {
    val request = get("/assets/i-do-not-exist")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isNotFound)
  }
}
