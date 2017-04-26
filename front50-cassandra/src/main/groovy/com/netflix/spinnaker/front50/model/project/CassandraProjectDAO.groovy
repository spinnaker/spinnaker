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


package com.netflix.spinnaker.front50.model.project

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.OperationResult
import com.netflix.astyanax.connectionpool.exceptions.BadRequestException
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.model.ColumnList
import com.netflix.astyanax.model.Row
import com.netflix.astyanax.retry.ExponentialBackoff
import com.netflix.astyanax.serializers.IntegerSerializer
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.spinnaker.front50.config.CassandraConfigProps
import com.netflix.spinnaker.front50.exception.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Slf4j
@Component
@ConditionalOnExpression('${cassandra.enabled:false}')
class CassandraProjectDAO implements ProjectDAO {
  private static final String CF_NAME = 'project'
  private static final String TEST_QUERY = '''select * from project;'''
  private static ColumnFamily<Integer, String> CF_PROJECT = ColumnFamily.newColumnFamily(
      CF_NAME, IntegerSerializer.get(), StringSerializer.get()
  )

  @Autowired
  CassandraConfigProps cassandraConfigProps

  @Autowired
  Keyspace keyspace

  @Autowired
  ObjectMapper objectMapper

  @PostConstruct
  void init() {
    try {
      runQuery(TEST_QUERY)
    } catch (BadRequestException ignored) {
      runQuery '''
              CREATE TABLE project(
                id timeuuid,
                name varchar,
                email varchar,
                updatets bigint,
                createts bigint,
                config varchar,
                PRIMARY KEY (id)
             ) with compression={};
               '''

      runQuery '''
              CREATE INDEX project_name ON project( name );
               '''
    }
  }

  Project findBy(String fieldName, String fieldValue) throws NotFoundException {
    def project = all().find {
      it."${fieldName}"?.trim()?.toLowerCase() == fieldValue.trim().toLowerCase()
    }

    if (!project) {
      throw new NotFoundException("No Project found (${fieldName}: ${fieldValue})")
    }

    return project
  }

  @Override
  Project findByName(String name) throws NotFoundException {
    return findBy("name", name)
  }

  @Override
  Project findById(String id) throws NotFoundException {
    return findBy("id", id)
  }

  @Override
  Set<Project> all() {
    all(true)
  }

  @Override
  Collection<Project> all(boolean refresh) {
    return unmarshallResults(runQuery('SELECT * FROM project;'))
  }

  @Override
  Collection<Project> history(String id, int maxResults) {
    return [findById(id)]
  }

  Project create(String id, Project project) {
    runQuery(buildInsertQuery(objectMapper, project))
    return findBy("name", project.name)
  }

  void update(String id, Project project) {
    runQuery(buildInsertQuery(objectMapper, project))
  }

  void delete(String id) {
    runQuery("DELETE FROM project WHERE id = ${id};")
  }

  @Override
  void bulkImport(Collection<Project> projects) {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isHealthy() {
    return true
  }

  void truncate() {
    keyspace.truncateColumnFamily(CF_NAME)
  }

  private List<Project> unmarshallResults(OperationResult result) {
    return result.result.rows.collect { Row<Integer, String> row ->
      ColumnList columns = row.columns

      def getStringValue = { String columnName ->
        def column = columns.getColumnByName(columnName)
        return column.hasValue() ? (column.stringValue ?: null) : null
      }

      def getLongValue = {
        String columnName ->
          def column = columns.getColumnByName(columnName)
          return column.hasValue() ? (column.longValue ?: null) : null
      }

      def config = objectMapper.readValue(getStringValue("config"), Project.ProjectConfig)

      return new Project(
          id: row.columns.getColumnByName('id').UUIDValue.toString(),
          name: getStringValue("name"),
          email: getStringValue("email"),
          createTs: getLongValue("createts"),
          updateTs: getLongValue("updatets"),
          config: config
      )
    }
  }

  private OperationResult runQuery(String query) {
    return keyspace
        .prepareQuery(CF_PROJECT)
        .withRetryPolicy(new ExponentialBackoff(cassandraConfigProps.exponentialBaseSleepTimeMs, cassandraConfigProps.exponentialMaxAttempts))
        .withCql(query)
        .execute()
  }

  private OperationResult runQuery(Query query) {
    def preparedQuery = keyspace
        .prepareQuery(CF_PROJECT)
        .withRetryPolicy(new ExponentialBackoff(cassandraConfigProps.exponentialBaseSleepTimeMs, cassandraConfigProps.exponentialMaxAttempts))
        .withCql(query.cql)
        .asPreparedStatement()
    query.values.each {
      if (it instanceof Long) {
        preparedQuery = preparedQuery.withLongValue(it as Long)
      } else {
        preparedQuery = preparedQuery.withStringValue(it ? it.toString() : "")
      }
    }

    return preparedQuery.execute()
  }

  private static Query buildInsertQuery(ObjectMapper objectMapper, Project project) {
    def fields = ["name", "email", "createts", "updatets", "config"]
    def values = [project.name, project.email, project.createTs, project.updateTs, objectMapper.writeValueAsString(project.config)]

    def idValue = project.id ?: "now()"
    def cql = """INSERT INTO project (id, ${fields.join(",")}) VALUES (${idValue}, ${fields.collect { "?" }.join(",")});"""
    return new Query(cql: cql, values: values)
  }

  private static class Query {
    String cql
    List<Object> values
  }
}
