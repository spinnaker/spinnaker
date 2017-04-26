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
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Repository

import javax.annotation.PostConstruct

/**
 * Repository for presets
 */
@Slf4j
@Repository
@SuppressWarnings('PropertyName')
@ConditionalOnExpression('${cassandra.enabled:true}')
class StrategyRepository implements PipelineStrategyDAO {

    @Autowired
    Keyspace keyspace

    static ColumnFamily<Integer, String> CF_PIPELINES
    static final String CF_NAME = 'strategyConfigs'
    ObjectMapper mapper = new ObjectMapper()

    @PostConstruct
    void init() {
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

    @Override
    Pipeline findById(String id) throws NotFoundException {
        def result = runQuery(
            "SELECT id, name, definition FROM strategy WHERE id = ${id};"
        )
        def strategies = resolvePipelines(result)

        if (!strategies) {
            throw new NotFoundException("No strategy found with id '${id}'")
        }
        return strategies[0]
    }

    List<Pipeline> all() {
      return all(true)
    }

  @Override
  Collection<Pipeline> all(boolean refresh) {
    def result = runQuery("""SELECT id, name, definition FROM strategy;""", true)
    resolvePipelines(result)
  }

  @Override
  Collection<Pipeline> history(String id, int maxResults) {
    return []
  }

  @Override
    Pipeline create(String id, Pipeline item) {
        update(id, item)
        return findById(getPipelineId(item.getApplication(), item.getName()))
    }

    @Override
    void update(String id, Pipeline item) {
        if (!item.id) {
            id = getPipelineId(item.application as String, item.name as String)
        } else {
            id = item.id
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
                '${sanitize(item.application as String)}',
                '${sanitize(item.name as String)}',
                '${sanitize(mapper.writeValueAsString(item))}'
            );
            """
        )
    }

    @Override
    void delete(String id) {
        runQuery(
            "DELETE FROM strategy WHERE id = ${id};"
        )
    }

    @Override
    void bulkImport(Collection<Pipeline> items) {
        items.each {
            try {
                log.info "Trying to insert ${it.application} : ${it.name}"
                update(it.getId(), it)
            } catch (Exception e) {
                log.error('could not process {}', it)
            }
        }
    }

    @Override
    boolean isHealthy() {
        return true
    }

    List<Pipeline> getPipelinesByApplication(String application) {
        def result = runQuery(
            """SELECT id, name, definition FROM strategy where application = '${sanitize(application)}';""",
            true
        )
        resolvePipelines(result)
    }

    String getPipelineId(String application, String name) {
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

    private String sanitize(String val) {
        val.replaceAll("'", "''")
    }

    private List<Pipeline> resolvePipelines(result) {
        List<Pipeline> strategies = []
        if (result != null && result.result?.rows?.rows) {
            result.result.rows.rows.each { row ->
                def id
                try {
                    id = row.columns.getColumnByName('id').UUIDValue.toString()
                    String name = row.columns.getColumnByName('name').stringValue
                    def strategy = mapper.readValue(row.columns.getColumnByName('definition').stringValue, Pipeline)
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
