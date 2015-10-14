/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.OperationResult
import com.netflix.astyanax.connectionpool.exceptions.BadRequestException
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.retry.RetryNTimes
import com.netflix.astyanax.serializers.IntegerSerializer
import com.netflix.astyanax.serializers.StringSerializer
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Repository

/**
 * Repository for presets
 */
@Repository
@SuppressWarnings('PropertyName')
@Slf4j
class StrategyRepository implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    Keyspace keyspace

    static ColumnFamily<Integer, String> CF_PIPELINES
    static final String CF_NAME = 'strategyConfigs'
    ObjectMapper mapper = new ObjectMapper()

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        CF_PIPELINES = ColumnFamily.newColumnFamily(CF_NAME, IntegerSerializer.get(), StringSerializer.get())
        try {
            runQuery '''select * from strategy limit 1;'''
        } catch (BadRequestException ignored) {
            runQuery '''
                CREATE TABLE strategy (
                    id timeuuid primary key,
                    application varchar,
                    name varchar,
                    definition text
                )
            '''
            runQuery '''
                CREATE INDEX strategy_application ON strategy( application );
            '''

            runQuery '''
                CREATE INDEX strategy_names ON strategy( name );
            '''
        }
    }

    List<Map> list() {
        def result = runQuery("""SELECT id, name, definition FROM strategy;""", true)
        resolvePipelines(result)
    }

    List<Map> getPipelinesByApplication(String application) {
        def result = runQuery(
            """SELECT id, name, definition FROM strategy where application = '${sanitize(application)}';""",
            true
        )
        resolvePipelines(result)
    }

    String get(String application, String name) {
        String id
        def result = runQuery(
            "SELECT id, application FROM strategy WHERE name = '${sanitize(name)}';",
            true
        ).result
        if (result.hasRows()) {
            result.rows.rows.each { row ->
                if (row.columns.getColumnByName('application').stringValue == application) {
                    id = row.columns.getColumnByName('id').UUIDValue.toString()
                }
            }
        }
        id
    }

    void rename(String application, String currentName, String newName) {
        String id = get(application, currentName)
        if (id) {
            runQuery(
                "UPDATE strategy SET name = '${sanitize(newName)}' WHERE id = ${id};"
            )
        }
    }

    void save(Map strategy) {
        String id
        if (!strategy.id) {
            id = get(strategy.application, strategy.name)
        } else {
            id = strategy.id
        }
        runQuery(
            """
            INSERT INTO strategy(
                id,
                application,
                name,
                definition)
            VALUES(
                ${id ?: 'now()'},
                '${sanitize(strategy.application)}',
                '${sanitize(strategy.name)}',
                '${sanitize(mapper.writeValueAsString(strategy))}'
            );
            """
        )
    }

    private String sanitize(String val) {
        val.replaceAll("'", "''")
    }

    void delete(String application, String name) {
        String id = get(application, name)
        deleteById(id)
    }

    void batchUpdate(List<Map> strategies) {
        strategies.each {
            try {
                log.info "Trying to insert ${it.application} : ${it.name}"
                save(it)
            } catch (Exception e) {
                log.error('could not process {}', it)
            }
        }
    }

    void deleteById(String id) {
        if (id) {
            runQuery(
                "DELETE FROM strategy WHERE id = ${id};"
            )
        }
    }

    private List<Map> resolvePipelines(result) {
        List<Map> strategies = []
        if (result != null && result.result?.rows?.rows) {
            result.result.rows.rows.each { row ->
                def id
                try {
                    id = row.columns.getColumnByName('id').UUIDValue.toString()
                    String name = row.columns.getColumnByName('name').stringValue
                    def strategy = mapper.readValue(row.columns.getColumnByName('definition').stringValue, Map)
                    strategy.id = id
                    strategy.name = name
                    strategies.add(strategy)
                } catch (e) {
                    log.error("could not process value ${id}", e)
                }
            }
        }
        strategies
    }

    @VisibleForTesting
    private OperationResult runQuery(String query, boolean isRead = false) {
        def preparedQuery = keyspace.prepareQuery(CF_PIPELINES)

        if (isRead) {
            preparedQuery.withCaching(true)
        }

        def preparedStatement = preparedQuery
            .withRetryPolicy(new RetryNTimes(5))
            .withCql(query)

        return preparedStatement.execute()
    }
}
