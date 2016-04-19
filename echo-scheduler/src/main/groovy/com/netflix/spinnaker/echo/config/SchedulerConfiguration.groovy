/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config
import com.netflix.astyanax.Keyspace
import com.netflix.fenzo.triggers.TriggerOperator
import com.netflix.fenzo.triggers.persistence.InMemoryTriggerDao
import com.netflix.fenzo.triggers.persistence.TriggerDao
import com.netflix.scheduledactions.ActionsOperator
import com.netflix.scheduledactions.DaoConfigurer
import com.netflix.scheduledactions.persistence.ActionInstanceDao
import com.netflix.scheduledactions.persistence.ExecutionDao
import com.netflix.scheduledactions.persistence.InMemoryActionInstanceDao
import com.netflix.scheduledactions.persistence.InMemoryExecutionDao
import com.netflix.scheduledactions.persistence.cassandra.CassandraActionInstanceDao
import com.netflix.scheduledactions.persistence.cassandra.CassandraExecutionDao
import com.netflix.scheduledactions.persistence.cassandra.CassandraTriggerDao
import com.netflix.scheduledactions.web.controllers.ActionInstanceController
import com.squareup.okhttp.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import retrofit.client.Client
import retrofit.client.OkClient

import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnExpression('${scheduler.enabled:false}')
@ComponentScan(["com.netflix.spinnaker.echo.scheduler", "com.netflix.scheduledactions"])
class SchedulerConfiguration {

    @Bean
    @ConditionalOnExpression('${spinnaker.cassandra.enabled:true}')
    ActionInstanceDao actionInstanceDao(Keyspace keyspace) {
        new CassandraActionInstanceDao(keyspace)
    }

    @Bean
    @ConditionalOnExpression('${spinnaker.cassandra.enabled:true}')
    ExecutionDao executionDao(Keyspace keyspace) {
        new CassandraExecutionDao(keyspace)
    }

    @Bean
    @ConditionalOnExpression('${spinnaker.cassandra.enabled:true}')
    TriggerDao triggerDao(Keyspace keyspace) {
        new CassandraTriggerDao(keyspace)
    }

    @Bean
    @ConditionalOnExpression('${spinnaker.inMemory.enabled:false}')
    ActionInstanceDao inMemoryActionInstanceDAO() {
        new InMemoryActionInstanceDao()
    }

    @Bean
    @ConditionalOnExpression('${spinnaker.inMemory.enabled:false}')
    ExecutionDao inMemoryExecutionDao() {
        new InMemoryExecutionDao()
    }

    @Bean
    @ConditionalOnExpression('${spinnaker.inMemory.enabled:false}')
    TriggerDao inMemoryTriggerDao() {
        new InMemoryTriggerDao()
    }

    @Bean
    DaoConfigurer daoConfigurer(ActionInstanceDao actionInstanceDao, TriggerDao triggerDao, ExecutionDao executionDao) {
        return new DaoConfigurer(actionInstanceDao, triggerDao, executionDao)
    }

    @Bean
    TriggerOperator triggerOperator(TriggerDao triggerDao,
                                    @Value('${scheduler.threadPoolSize:10}') int threadPoolSize) {
        new TriggerOperator(triggerDao, threadPoolSize)
    }

    @Bean
    ActionsOperator actionsOperator(TriggerOperator triggerOperator,
                                    DaoConfigurer daoConfigurer,
                                    @Value('${scheduler.threadPoolSize:10}') int threadPoolSize) {
        new ActionsOperator(triggerOperator, daoConfigurer, threadPoolSize)
    }

    @Bean
    ActionInstanceController actionInstanceController(ActionsOperator actionsOperator) {
        new ActionInstanceController(actionsOperator: actionsOperator)
    }

    @Bean
    Client retrofitClient(@Value('${retrofit.connectTimeoutMillis:10000}') long connectTimeoutMillis,
                          @Value('${retrofit.readTimeoutMillis:15000}') long readTimeoutMillis) {
        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS);
        okHttpClient.setReadTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS);
        new OkClient(okHttpClient);
    }
}
