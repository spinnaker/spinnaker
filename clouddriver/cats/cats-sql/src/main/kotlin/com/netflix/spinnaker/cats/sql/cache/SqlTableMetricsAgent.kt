package com.netflix.spinnaker.cats.sql.cache

import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.cats.sql.SqlUtil
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.sql.SqlAgent
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory

class SqlTableMetricsAgent(
  private val jooq: DSLContext,
  private val registry: MeterRegistry,
  private val clock: Clock,
  private val namespace: String?
) : RunnableAgent, CustomScheduledAgent, SqlAgent {

  companion object {
    private val DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1)
    private val DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2)

    private val log = LoggerFactory.getLogger(SqlTableMetricsAgent::class.java)
  }

  private val namespaceTags = Tags.of("namespace", namespace ?: "none")

  private val countGauges = mutableMapOf<String, AtomicReference<Double>>()

  private val timingGauge = registry.gauge(
    "cats.sqlCache.tableMetricsAgent.timing", namespaceTags, AtomicReference(0.0)
  ) { it.get() }

  override fun run() {
    val start = clock.millis()
    var tableCount = 0

    val baseName = if (namespace == null) {
      "cats_v${SqlSchemaVersion.current()}_"
    } else {
      "cats_v${SqlSchemaVersion.current()}_${namespace}_"
    }

    val rs = SqlUtil.getTablesLike(jooq, baseName)
    while (rs.next()) {
      val tableName = rs.getString(1)
      val type = tableName.replace(baseName, "")

      val count = jooq.selectCount()
        .from(table(tableName))
        .fetchSingle()
        .value1()

      val countGauge = countGauges.getOrPut(type) {
        registry.gauge(
          "cats.sqlCache.tableMetricsAgent.count",
          namespaceTags.and("type", type),
          AtomicReference(0.0)
        ) { it.get() }!!
      }
      countGauge.set(count.toDouble())
      tableCount++
    }

    val runTime = clock.millis() - start
    timingGauge!!.set(runTime.toDouble())
    log.info("Read counts for $tableCount tables in ${runTime}ms")
  }

  override fun getAgentType(): String = javaClass.simpleName
  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = DEFAULT_POLL_INTERVAL_MILLIS
  override fun getTimeoutMillis(): Long = DEFAULT_TIMEOUT_MILLIS
}
