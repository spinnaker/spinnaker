package com.netflix.spinnaker.keel.sql.spring

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.sql.SqlLock
import com.netflix.spinnaker.keel.sql.SqlResourceRepository
import com.netflix.spinnaker.keel.sync.Lock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectThat
import strikt.assertions.isA

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class],
  webEnvironment = MOCK,
  properties = [
    "sql.enabled=true",
    "sql.connection-pools.default.jdbc-url=jdbc:h2:mem:keel;MODE=MYSQL",
    "sql.migration.jdbc-url=jdbc:h2:mem:keel;MODE=MYSQL"
  ]
)
internal class SpringStartupTests {

  @Autowired
  lateinit var resourceRepository: ResourceRepository

  @Autowired
  lateinit var lock: Lock

  @Test
  fun `uses RedisResourceRepository`() {
    expectThat(resourceRepository).isA<SqlResourceRepository>()
  }

  @Test
  fun `uses RedisLock`() {
    expectThat(lock).isA<SqlLock>()
  }
}
