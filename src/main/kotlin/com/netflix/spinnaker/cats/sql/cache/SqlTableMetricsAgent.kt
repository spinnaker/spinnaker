package com.netflix.spinnaker.cats.sql.cache

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import org.jooq.DSLContext
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.TimeUnit

class SqlTableMetricsAgent(
  private val jooq: DSLContext,
  private val registry: Registry,
  private val clock: Clock,
  private val namespace: String?
) : RunnableAgent, CustomScheduledAgent {

  companion object {
    private val DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1)
    private val DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2)

    private val log = LoggerFactory.getLogger(SqlTableMetricsAgent::class.java)
  }

  private val countId = registry.createId("cats.sqlCache.tableMetricsAgent.count")
    .withTag("namespace", namespace ?: "none")

  private val timingId = registry.createId("cats.sqlCache.tableMetricsAgent.timing")
    .withTag("namespace", namespace ?: "none")

  override fun run() {
    val start = clock.millis()
    var tableCount = 0

    val baseName = if (namespace == null) {
      "cats_v${SqlSchemaVersion.current()}_"
    } else {
      "cats_v${SqlSchemaVersion.current()}_${namespace}_"
    }

    val rs = jooq.fetch("show tables like '$baseName%'").intoResultSet()

    while (rs.next()) {
      val tableName = rs.getString(1)
      val type = tableName.replace(baseName, "")

      val count = jooq.selectCount()
        .from(table(tableName))
        .fetchOne(0, Int::class.java)

      registry.gauge(countId.withTag("type", type)).set(count.toDouble())
      tableCount++
    }

    val runTime = clock.millis() - start
    registry.gauge(timingId).set(runTime.toDouble())
    log.info("Read counts for $tableCount tables in ${runTime}ms")
  }

  override fun getAgentType(): String = javaClass.simpleName
  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = DEFAULT_POLL_INTERVAL_MILLIS
  override fun getTimeoutMillis(): Long = DEFAULT_TIMEOUT_MILLIS
}
