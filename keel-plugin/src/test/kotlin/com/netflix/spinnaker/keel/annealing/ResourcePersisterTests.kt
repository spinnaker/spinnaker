package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.ApplicationEventPublisher

@AutoConfigureMockMvc
internal class ResourcePersisterTests : JUnit5Minutests {

  data class Fixture(
    val subject: ResourcePersister,
    val repository: InMemoryResourceRepository = InMemoryResourceRepository(),
    val queue: ResourceCheckQueue = mockk(),
    val publisher: ApplicationEventPublisher = mockk()
  )

  fun tests() = rootContext<ResourcePersister> {

  }

}
