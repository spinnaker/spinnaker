/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.sql.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.sql.telemetry.HikariSpectatorMetricsTrackerFactory
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides a default HikariCP [DataSourceFactory].
 */
@Configuration
@ConditionalOnMissingBean(DataSourceFactory::class)
class HikariDataSourceConfiguration {

  @Bean
  fun hikariMetricsTrackerFactory(registry: Registry): MetricsTrackerFactory =
    HikariSpectatorMetricsTrackerFactory(registry)

  @Bean
  fun dataSourceFactory(metricsTrackerFactory: MetricsTrackerFactory): DataSourceFactory =
    HikariDataSourceFactory(metricsTrackerFactory)
}
