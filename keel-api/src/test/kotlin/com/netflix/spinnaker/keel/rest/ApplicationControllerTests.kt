package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.persistence.memory.InMemoryApplicationVetoRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.resource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class ApplicationControllerTests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @Autowired
  lateinit var applicationVetoRepository: InMemoryApplicationVetoRepository

  @AfterEach
  fun clearRepository() {
    resourceRepository.dropAll()
    applicationVetoRepository.flush()
  }

  @Test
  fun `can get resource summaries by application`() {
    val res = resource()
    resourceRepository.store(res)
    resourceRepository.appendHistory(ResourceCreated(res))

    var request = get("/application/${res.application}")
      .accept(MediaType.APPLICATION_JSON_VALUE)
    mvc
      .perform(request)
      .andExpect(status().isOk)
      .andExpect(content().string(
        """{"hasManagedResources":true}"""
      ))

    request = get("/application/${res.application}?includeDetails=true")
      .accept(MediaType.APPLICATION_JSON_VALUE)
    mvc
      .perform(request)
      .andExpect(status().isOk)
      .andExpect(content().string(
        """{"hasManagedResources":true,"resources":[{"id":"${res.id}","kind":"${res.kind}","status":"CREATED"}]}"""
      ))
  }
}
