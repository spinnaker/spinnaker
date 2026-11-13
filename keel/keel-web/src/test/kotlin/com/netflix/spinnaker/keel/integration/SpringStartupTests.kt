package com.netflix.spinnaker.keel.integration

import com.netflix.spinnaker.kork.metrics.MeterRegistryAssertions
import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationListener

@SpringBootTest(webEnvironment = NONE)
internal class SpringStartupTests {

  @MockkBean(relaxUnitFun = true, relaxed = true)
  lateinit var applicationReadyListener: ApplicationListener<ApplicationReadyEvent>

  @Test
  fun `the application starts successfully`() {
    verify(timeout = 8000) {
      applicationReadyListener.onApplicationEvent(ofType())
    }
  }

  @Autowired
  lateinit var context: ApplicationContext

  @Test
  fun `metrics are exportable by default`() {
    MeterRegistryAssertions.assertExportableByDefault(context)
  }
}
