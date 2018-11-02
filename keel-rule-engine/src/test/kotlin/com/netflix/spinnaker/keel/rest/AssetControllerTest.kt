package com.netflix.spinnaker.keel.rest

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.RuleEngineApp
import com.netflix.spinnaker.keel.processing.AssetService
import com.netflix.spinnaker.keel.processing.VetoService
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [RuleEngineApp::class, MockPluginConfig::class, MockEurekaConfig::class],
  properties = [
    "clouddriver.baseUrl=https://localhost:8081",
    "orca.baseUrl=https://localhost:8082"
  ],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
internal class AssetControllerTest {
  @Autowired
  lateinit var mvc: MockMvc

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
}

@Configuration
class MockPluginConfig {
  @MockBean
  lateinit var assetService: AssetService

  @MockBean
  lateinit var vetoService: VetoService
}

@Configuration
class MockEurekaConfig {
  @MockBean
  lateinit var eurekaClient: EurekaClient

  @Bean
  fun currentInstance(): InstanceInfo = InstanceInfo.Builder.newBuilder()
    .run {
      setAppName("keel")
      setASGName("keel-local")
      build()
    }
}
