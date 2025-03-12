/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository
import com.netflix.spinnaker.fiat.permissions.SqlPermissionsRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import com.netflix.spinnaker.kork.telemetry.InstrumentedProxy
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.slf4j.MDCContext
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock
import kotlin.contracts.ExperimentalContracts

const val coroutineThreadPrefix = "sql"

@ExperimentalContracts
@Configuration
@ConditionalOnProperty("sql.enabled")
@Import(DefaultSqlConfiguration::class)
class SqlConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(SqlConfiguration::class.java)
    }

    /*
     * permissionsRepository.sql.asyncPoolSize: If set to a positive integer, a fixed thread pool of this size is created
     * as part of a coroutineContext. If permissionsRepository.sql.maxQueryConcurrency is also >1 (default value: 4),
     * sql queries to fetch > 2 * permissionsRepository.sql.readBatchSize values will be made asynchronously in batches of
     * maxQueryConcurrency size.
     */
    @ConditionalOnProperty("permissions-repository.sql.enabled")
    @Bean
    @ObsoleteCoroutinesApi
    fun sqlPermissionsRepository(
        objectMapper: ObjectMapper,
        registry: Registry,
        jooq: DSLContext,
        sqlProperties: SqlProperties,
        resources: List<Resource>,
        dynamicConfigService: DynamicConfigService,
        @Value("\${permissions-repository.sql.async-pool-size:0}") poolSize: Int
    ): PermissionsRepository {

        /**
         * newFixedThreadPoolContext was marked obsolete in Oct 2018, to be reimplemented as a new
         * concurrency limiting threaded context factory with reduced context switch overhead. As of
         * Feb 2019, the new implementation is unreleased. See: https://github.com/Kotlin/kotlinx.coroutines/issues/261
         *
         * TODO: switch to newFixedThreadPoolContext's replacement when ready
         */
        val dispatcher = if (poolSize < 1) {
            null
        } else {
            newFixedThreadPoolContext(nThreads = poolSize, name = coroutineThreadPrefix) + MDCContext()
        }

        if (dispatcher != null) {
            log.info("Configured coroutine context with newFixedThreadPoolContext of $poolSize threads")
        }

        return SqlPermissionsRepository(
            Clock.systemUTC(),
            objectMapper,
            jooq,
            sqlProperties.retries,
            resources,
            dispatcher,
            dynamicConfigService
        ).let {
            InstrumentedProxy.proxy(
                registry,
                it,
                "permissionsRepository",
                mapOf(Pair("repositoryType", "sql"))
            ) as PermissionsRepository
        }
    }
}
