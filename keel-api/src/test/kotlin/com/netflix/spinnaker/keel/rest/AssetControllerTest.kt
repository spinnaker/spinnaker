package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.redis.spring.EmbeddedRedisConfiguration
import com.netflix.spinnaker.keel.redis.spring.MockEurekaConfig
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfig::class, EmbeddedRedisConfiguration::class],
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
