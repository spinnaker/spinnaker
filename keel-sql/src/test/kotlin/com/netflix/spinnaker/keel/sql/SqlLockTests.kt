package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.sync.LockTests
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlLockTests : LockTests<SqlLock>() {

  private val jooq = initDatabase("jdbc:h2:mem:keel;MODE=MYSQL")

  @JvmStatic
  @AfterAll
  fun shutdown() {
    jooq.close()
    shutdown("jdbc:h2:mem:keel")
  }

  override fun subject(clock: Clock): SqlLock = SqlLock(jooq, clock)

  override fun flush() {
    jooq.flushAll()
  }
}
