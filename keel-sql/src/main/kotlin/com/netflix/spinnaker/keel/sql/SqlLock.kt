package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.sync.Lock
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration

class SqlLock(
  private val jooq: DSLContext,
  private val clock: Clock
) : Lock {
  override fun tryAcquire(name: String, duration: Duration): Boolean =
    jooq.inTransaction {
      val now = clock.instant()
      val limit = now.let(Timestamp::from)
      val expires = now.plus(duration).let(Timestamp::from)
      selectOne()
        .from(CLUSTER_LOCK)
        .where(NAME.eq(name))
        .forUpdate()
        .fetch()
        .intoResultSet()
        .let {
          if (it.next()) {
            update(CLUSTER_LOCK)
              .set(EXPIRES, expires)
              .where(NAME.eq(name), EXPIRES.lt(limit))
              .execute() > 0
          } else {
            insertInto(CLUSTER_LOCK, NAME, EXPIRES)
              .values(name, expires)
              .execute() > 0
          }
        }
    }

  companion object {
    val CLUSTER_LOCK: Table<Record> = table("cluster_lock")
    val NAME: Field<Any> = field("name")
    val EXPIRES: Field<Any> = field("expires")
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
