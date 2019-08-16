package com.netflix.spinnaker.keel.integration

import com.netflix.spinnaker.keel.KeelApplication
import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.ApplicationListener
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class],
  webEnvironment = MOCK,
  properties = [
    "sql.enabled=true",
    "keel.plugins.ec2.enabled=true",
    "clouddriver.enabled=true",
    "clouddriver.base-url=http://localhost:8080",
    "orca.enabled=true",
    "orca.base-url=http://localhost:8080",
    "sql.connection-pools.default.jdbc-url=jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    "sql.migration.jdbc-url=jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver"
  ]
)
// this just avoids some noise if scheduled tasks start running (Orca & CloudDriver endpoints ^^^ are a lie)
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
