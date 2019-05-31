package com.netflix.spinnaker.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.sql.MariaDbConnectionPoolMetricsExporter
import com.netflix.spinnaker.sql.MariaDbDataSourceFactory
import com.netflix.spinnaker.kork.sql.config.DataSourceFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("sql.enabled")
@ComponentScan("com.netflix.spinnaker.sql")
class MariaDbDataSourceConfiguration {

  @Bean
  fun connectionPoolMetricsExporter(registry: Registry): MariaDbConnectionPoolMetricsExporter {
    return MariaDbConnectionPoolMetricsExporter(registry)
  }

  @Bean
  fun dataSourceFactory(connectionPoolMetricsExporter: MariaDbConnectionPoolMetricsExporter): DataSourceFactory =
    MariaDbDataSourceFactory(connectionPoolMetricsExporter)
}
