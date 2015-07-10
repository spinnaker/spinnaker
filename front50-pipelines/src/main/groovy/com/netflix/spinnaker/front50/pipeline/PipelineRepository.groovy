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
class PipelineRepository implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    Keyspace keyspace

    static ColumnFamily<Integer, String> CF_PIPELINES
    static final String CF_NAME = 'pipelineConfigs'
    ObjectMapper mapper = new ObjectMapper()

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        CF_PIPELINES = ColumnFamily.newColumnFamily(CF_NAME, IntegerSerializer.get(), StringSerializer.get())
        try {
            runQuery '''select * from pipeline limit 1;'''
        } catch (BadRequestException ignored) {
            runQuery '''
                CREATE TABLE pipeline (
                    id timeuuid primary key,
                    application varchar,
                    name varchar,
                    definition text
                )
            '''
            runQuery '''
                CREATE INDEX pipeline_application ON pipeline( application );
            '''

            runQuery '''
                CREATE INDEX pipeline_names ON pipeline( name );
            '''
        }
    }

    List<Map> list() {
        def result = runQuery("""SELECT id, name, definition FROM pipeline;""", true)
        resolvePipelines(result)
    }

    List<Map> getPipelinesByApplication(String application) {
        def result = runQuery(
            """SELECT id, name, definition FROM pipeline where application = '${sanitize(application)}';""",
            true
        )
        resolvePipelines(result)
    }

    String get(String application, String name) {
        String id
        def result = runQuery(
            "SELECT id, application FROM pipeline WHERE name = '${sanitize(name)}';",
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
                "UPDATE pipeline SET name = '${sanitize(newName)}' WHERE id = ${id};"
            )
        }
    }

    void save(Map pipeline) {
        String id
        if (!pipeline.id) {
            id = get(pipeline.application, pipeline.name)
        } else {
            id = pipeline.id
        }
        runQuery(
            """
            INSERT INTO pipeline(
                id,
                application,
                name,
                definition)
            VALUES(
                ${id ?: 'now()'},
                '${sanitize(pipeline.application)}',
                '${sanitize(pipeline.name)}',
                '${sanitize(mapper.writeValueAsString(pipeline))}'
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

    void batchUpdate(List<Map> pipelines) {
        pipelines.each {
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
                "DELETE FROM pipeline WHERE id = ${id};"
            )
        }
    }

    private List<Map> resolvePipelines(result) {
        List<Map> pipelines = []
        if (result != null && result.result?.rows?.rows) {
            result.result.rows.rows.each { row ->
                def id
                try {
                    id = row.columns.getColumnByName('id').UUIDValue.toString()
                    String name = row.columns.getColumnByName('name').stringValue
                    def pipeline = mapper.readValue(row.columns.getColumnByName('definition').stringValue, Map)
                    pipeline.id = id
                    pipeline.name = name
                    pipelines.add(pipeline)
                } catch (e) {
                    log.error("could not process value ${id}", e)
                }
            }
        }
        pipelines
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
