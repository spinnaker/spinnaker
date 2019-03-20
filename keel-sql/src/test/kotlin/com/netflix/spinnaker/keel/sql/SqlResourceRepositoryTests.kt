package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import com.netflix.spinnaker.keel.persistence.ResourceRepositoryTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlResourceRepositoryTests : ResourceRepositoryTests<SqlResourceRepository>() {
  private val jooq = initDatabase("jdbc:h2:mem:keel;MODE=MYSQL")

  override fun factory(clock: Clock): SqlResourceRepository {
    return SqlResourceRepository(
      jooq,
      configuredObjectMapper(),
      clock,
      object : InstanceIdSupplier {
        override fun get() = "localhost"
      }
    )
  }

  override fun flush() {
    jooq.flushAll()
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    jooq.close()
    shutdown("jdbc:h2:mem:keel")
  }
}
