package com.netflix.spinnaker.keel.integration

import com.netflix.spinnaker.keel.KeelApplication
import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.ApplicationListener

@SpringBootTest(
  classes = [KeelApplication::class],
  webEnvironment = MOCK
)
@EnableAutoConfiguration(exclude = [TaskSchedulingAutoConfiguration::class])
internal class SpringStartupTests {

  @MockkBean(relaxUnitFun = true)
  lateinit var applicationReadyListener: ApplicationListener<ApplicationReadyEvent>

  @Test
  fun `the application starts successfully`() {
    verify(timeout = 2000) {
      applicationReadyListener.onApplicationEvent(ofType())
    }
  }
}
