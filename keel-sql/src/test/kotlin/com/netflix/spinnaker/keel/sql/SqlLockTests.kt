package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.sync.LockTests
import org.jooq.SQLDialect.MYSQL_5_7
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlLockTests : LockTests<SqlLock>() {

  private val jooq = initDatabase(
    "jdbc:tc:mysql:5.7.22://somehostname:someport/databasename",
    MYSQL_5_7
  )

  @JvmStatic
  @AfterAll
  fun shutdown() {
    jooq.close()
  }

  override fun subject(clock: Clock): SqlLock = SqlLock(jooq, clock)

  override fun flush() {
    jooq.flushAll()
  }
}
