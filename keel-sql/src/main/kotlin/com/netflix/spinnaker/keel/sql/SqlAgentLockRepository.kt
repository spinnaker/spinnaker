package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.AgentLockRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.AGENT_LOCK
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import java.time.Clock
import org.jooq.DSLContext

class SqlAgentLockRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  override val agents: List<ScheduledAgent>,
  private val sqlRetry: SqlRetry
) : AgentLockRepository {

  override fun tryAcquireLock(agentName: String, lockTimeoutSeconds: Long): Boolean {
    val now = clock.instant()

    var changed = sqlRetry.withRetry(WRITE) {
      jooq.insertInto(AGENT_LOCK)
        .set(AGENT_LOCK.LOCK_NAME, agentName)
        .set(AGENT_LOCK.EXPIRY, now.plusSeconds(lockTimeoutSeconds).toTimestamp())
        .onDuplicateKeyIgnore()
        .execute()
    }

    if (changed == 0) {
      changed = sqlRetry.withRetry(WRITE) {
        jooq.update(AGENT_LOCK)
          .set(AGENT_LOCK.EXPIRY, now.plusSeconds(lockTimeoutSeconds).toTimestamp())
          .where(
            AGENT_LOCK.LOCK_NAME.eq(agentName),
            AGENT_LOCK.EXPIRY.lt(now.toTimestamp())
          )
          .execute()
      }
    }

    return changed == 1
  }

  override fun getLockedAgents(): List<String> {
    return sqlRetry.withRetry(READ) {
      jooq.select(AGENT_LOCK.LOCK_NAME)
        .from(AGENT_LOCK)
        .fetch(AGENT_LOCK.LOCK_NAME)
    }
  }
}
