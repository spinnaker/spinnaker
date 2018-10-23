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
package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.sql.JooqSqlCommentAppender
import com.netflix.spinnaker.orca.sql.JooqToSpringExceptionTransformer
import com.netflix.spinnaker.orca.sql.SpringLiquibaseProxy
import com.netflix.spinnaker.orca.sql.SqlHealthIndicator
import com.netflix.spinnaker.orca.sql.SqlHealthcheckActivator
import com.netflix.spinnaker.orca.sql.config.DataSourceConfiguration
import com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository
import com.netflix.spinnaker.orca.sql.telemetry.SlowQueryLogger
import com.netflix.spinnaker.orca.sql.telemetry.SqlInstrumentedExecutionRepository
import liquibase.integration.spring.SpringLiquibase
import org.jooq.DSLContext
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.jooq.impl.DefaultExecuteListenerProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import sun.net.InetAddressCachePolicy
import java.lang.reflect.Field
import java.security.Security
import java.sql.Connection
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty("sql.enabled")
@EnableConfigurationProperties(SqlProperties::class)
@Import(DataSourceConfiguration::class, DataSourceAutoConfiguration::class)
@ComponentScan("com.netflix.spinnaker.orca.sql")
class SqlConfiguration {

  init {
    // Ugh, jOOQ, why. WHY.
    System.setProperty("org.jooq.no-logo", "true")

    forceInetAddressCachePolicy()
    Security.setProperty("networkaddress.cache.ttl", "0");
  }

  @Bean fun liquibase(properties: SqlProperties): SpringLiquibase =
    SpringLiquibaseProxy(properties)

  @Bean fun transactionManager(dataSource: DataSource): DataSourceTransactionManager =
    DataSourceTransactionManager(dataSource)

  @Bean fun dataSourceConnectionProvider(dataSource: DataSource): DataSourceConnectionProvider =
    object: DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource)) {
      // Use READ COMMITTED if possible
      override fun acquire(): Connection = super.acquire().apply {
          if (metaData.supportsTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED)) {
            transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
          }
        }
    }

  @Bean fun jooqConfiguration(connectionProvider: DataSourceConnectionProvider,
                              properties: SqlProperties): DefaultConfiguration =
    DefaultConfiguration().apply {
      set(*DefaultExecuteListenerProvider.providers(
        JooqToSpringExceptionTransformer(),
        JooqSqlCommentAppender(),
        SlowQueryLogger()
      ))
      set(connectionProvider)
      setSQLDialect(properties.connectionPool.dialect)
    }

  @Bean(destroyMethod = "close") fun dsl(jooqConfiguration: DefaultConfiguration): DSLContext =
    DefaultDSLContext(jooqConfiguration)

  @ConditionalOnProperty("executionRepository.sql.enabled")
  @Bean fun sqlExecutionRepository(dsl: DSLContext,
                                   mapper: ObjectMapper,
                                   registry: Registry,
                                   properties: SqlProperties) =
    SqlInstrumentedExecutionRepository(
      SqlExecutionRepository(
        properties.partitionName,
        dsl,
        mapper,
        properties.transactionRetry,
        properties.batchReadSize
      ),
      registry
    )

  @Bean fun sqlHealthcheckActivator(dsl: DSLContext, registry: Registry) =
    SqlHealthcheckActivator(dsl, registry)

  @Bean("dbHealthIndicator") fun dbHealthIndicator(sqlHealthcheckActivator: SqlHealthcheckActivator,
                                                   sqlProperties: SqlProperties) =
    SqlHealthIndicator(sqlHealthcheckActivator, sqlProperties.connectionPool.dialect)
}

/**
 * When deployed with replicas, we want failover to be as fast as possible, so there's no DNS caching.
 */
private fun forceInetAddressCachePolicy() {
  if (InetAddressCachePolicy.get() != InetAddressCachePolicy.NEVER) {
    val field: Field
    try {
      field = InetAddressCachePolicy::class.java.getDeclaredField("cachePolicy")
      field.isAccessible = true
    } catch (e: NoSuchFieldException) {
      throw InetAddressOverrideFailure(e)
    } catch (e: SecurityException) {
      throw InetAddressOverrideFailure(e)
    }
    field.set(InetAddressCachePolicy::class.java.getDeclaredField("cachePolicy"), InetAddressCachePolicy.NEVER)
  }
}

class InetAddressOverrideFailure(cause: Throwable) : IllegalStateException(cause)
