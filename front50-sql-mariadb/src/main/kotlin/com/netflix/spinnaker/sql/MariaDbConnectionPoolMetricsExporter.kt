package com.netflix.spinnaker.sql

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.patterns.PolledMeter
import org.mariadb.jdbc.MariaDbPoolDataSource
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.atomic.AtomicLong

class MariaDbConnectionPoolMetricsExporter(
  private val registry: Registry
) {

  private val dataSourceMetrics: MutableList<DataSourceMetrics> = mutableListOf()

  fun track(dataSource: MariaDbPoolDataSource) {
    dataSourceMetrics.add(
      DataSourceMetrics(dataSource).apply {
        registerMeters(registry)
      }
    )
  }

  @Scheduled(fixedRate = 5_000)
  fun record() {
    dataSourceMetrics.forEach {
      // They tell me not to use test methods, but yolo. Way better than dealing with MBeans.
      it.dataSource.testGetPool()?.let { pool ->
        it.activeConnections.set(pool.activeConnections)
        it.totalConnections.set(pool.totalConnections)
        it.idleConnections.set(pool.idleConnections)
        it.blockedConnections.set(pool.connectionRequests)
      }
    }
  }
}

private data class DataSourceMetrics(
  val dataSource: MariaDbPoolDataSource,
  val activeConnections: AtomicLong = AtomicLong(),
  val totalConnections: AtomicLong = AtomicLong(),
  val idleConnections: AtomicLong = AtomicLong(),
  val blockedConnections: AtomicLong = AtomicLong()
) {

  fun registerMeters(registry: Registry) {
    monitorValue(registry, "active", activeConnections)
    monitorValue(registry, "total", totalConnections)
    monitorValue(registry, "idle", idleConnections)
    monitorValue(registry, "blocked", blockedConnections)
  }

  private fun monitorValue(registry: Registry, name: String, value: AtomicLong) {
    PolledMeter
      .using(registry)
      .withName("sql.pool.${dataSource.poolName}.$name")
      .monitorValue(value)
  }
}
