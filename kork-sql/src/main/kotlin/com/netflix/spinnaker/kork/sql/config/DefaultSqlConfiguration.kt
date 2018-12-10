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
import com.netflix.spinnaker.kork.sql.JooqSqlCommentAppender
import com.netflix.spinnaker.kork.sql.JooqToSpringExceptionTransformer
import com.netflix.spinnaker.kork.sql.health.SqlHealthIndicator
import com.netflix.spinnaker.kork.sql.health.SqlHealthProvider
import com.netflix.spinnaker.kork.sql.migration.SpringLiquibaseProxy
import com.netflix.spinnaker.kork.sql.telemetry.JooqSlowQueryLogger
import liquibase.integration.spring.SpringLiquibase
import org.jooq.DSLContext
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.jooq.impl.DefaultExecuteListenerProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
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
@EnableAutoConfiguration(exclude = [DataSourceAutoConfiguration::class])
@Import(DataSourceConfiguration::class)
class DefaultSqlConfiguration {

  init {
    System.setProperty("org.jooq.no-logo", "true")

    forceInetAddressCachePolicy()
    Security.setProperty("networkaddress.cache.ttl", "0");
  }

  @Bean
  @ConditionalOnMissingBean(SpringLiquibase::class)
  fun liquibase(properties: SqlProperties): SpringLiquibase =
    SpringLiquibaseProxy(properties.migration)

  @Bean
  @ConditionalOnMissingBean(DataSourceTransactionManager::class)
  fun transactionManager(dataSource: DataSource): DataSourceTransactionManager =
    DataSourceTransactionManager(dataSource)

  @Bean
  @ConditionalOnMissingBean(DataSourceConnectionProvider::class)
  fun dataSourceConnectionProvider(dataSource: DataSource): DataSourceConnectionProvider =
    object: DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource)) {
      // Use READ COMMITTED if possible
      override fun acquire(): Connection = super.acquire().apply {
        if (metaData.supportsTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED)) {
          transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        }
      }
    }

  @Bean
  @ConditionalOnMissingBean(DefaultConfiguration::class)
  fun jooqConfiguration(connectionProvider: DataSourceConnectionProvider,
                        properties: SqlProperties): DefaultConfiguration =
    DefaultConfiguration().apply {
      set(*DefaultExecuteListenerProvider.providers(
        JooqToSpringExceptionTransformer(),
        JooqSqlCommentAppender(),
        JooqSlowQueryLogger()
      ))
      set(connectionProvider)
      setSQLDialect(properties.connectionPool.dialect)
    }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(DSLContext::class)
  fun jooq(jooqConfiguration: DefaultConfiguration): DSLContext =
    DefaultDSLContext(jooqConfiguration)

  @Bean
  fun sqlHealthProvider(jooq: DSLContext,
                        registry: Registry,
                        @Value("\${sql.readOnly:false}") readOnly: Boolean): SqlHealthProvider =
    SqlHealthProvider(jooq, registry, readOnly)

  @Bean("dbHealthIndicator")
  fun dbHealthIndicator(sqlHealthProvider: SqlHealthProvider,
                        sqlProperties: SqlProperties) =
    SqlHealthIndicator(sqlHealthProvider, sqlProperties.connectionPool.dialect)
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
