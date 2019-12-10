package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.pause.ResourcePauser
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
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
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class AdminControllerTests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var deliveryConfigRepository: InMemoryDeliveryConfigRepository

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @Autowired
  lateinit var resourcePauser: ResourcePauser

  @AfterEach
  fun clearRepositories() {
    deliveryConfigRepository.dropAll()
    resourceRepository.dropAll()
  }

  var resource = resource()
  val deliveryConfig: DeliveryConfig = DeliveryConfig("keel", "fnord")

  @Test
  fun `application's resource does exists and can be deleted`() {
    resourceRepository.store(resource)

    val request = delete("/admin/applications/${resource.application}")
    mvc
      .perform(request)
      .andExpect(status().isNoContent)
  }

  @Test
  fun `application's resource not found when trying to delete it`() {
    val request = delete("/admin/applications/${resource.application}")
    mvc
      .perform(request)
      .andExpect(status().isNotFound)
  }

  @Test
  fun `application's delivery config does exists and can be deleted`() {
    deliveryConfigRepository.store(deliveryConfig)
    val request = delete("/admin/applications/${deliveryConfig.application}")
    mvc
      .perform(request)
      .andExpect(status().isNoContent)
  }

  @Test
  fun `application's delivery config does not exists when trying to delete it`() {
    val request = delete("/admin/applications/${deliveryConfig.application}")
    mvc
      .perform(request)
      .andExpect(status().isNotFound)
  }

  @Test
  fun `both application's delivery config and resource exists when trying to delete it`() {
    deliveryConfigRepository.store(deliveryConfig)
    resourceRepository.store(resource)
    val request = delete("/admin/applications/${deliveryConfig.application}")
    mvc
      .perform(request)
      .andExpect(status().isNoContent)
  }
}
