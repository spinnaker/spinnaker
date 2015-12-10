/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.front50.model.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.OperationResult
import com.netflix.astyanax.connectionpool.exceptions.BadRequestException
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.model.ColumnList
import com.netflix.astyanax.model.Row
import com.netflix.astyanax.retry.ExponentialBackoff
import com.netflix.astyanax.serializers.IntegerSerializer
import com.netflix.astyanax.serializers.ListSerializer
import com.netflix.astyanax.serializers.MapSerializer
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.spinnaker.front50.exception.NotFoundException
import groovy.util.logging.Slf4j
import org.apache.cassandra.db.marshal.UTF8Type
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Slf4j
@Component
@ConditionalOnExpression('${spinnaker.cassandra.enabled:false}')
class CassandraApplicationDAO implements ApplicationDAO {
  private static final MapSerializer<String, String> mapSerializer = new MapSerializer<String, String>(UTF8Type.instance, UTF8Type.instance)
  private static final ListSerializer<String> listSerializer = new ListSerializer(UTF8Type.instance)

  private static final String CF_NAME = 'application'
  private static final String TEST_QUERY = '''select * from application;'''
  private static ColumnFamily<Integer, String> CF_APPLICATION = ColumnFamily.newColumnFamily(
      CF_NAME, IntegerSerializer.get(), StringSerializer.get()
  )

  private static final Integer VERSION = 2

  @Value('${spinnaker.cassandra.exponentialBaseSleepTimeMs:250}')
  long exponentialBaseSleepTimeMs

  @Value('${spinnaker.cassandra.exponentialMaxAttempts:10}')
  int exponentialMaxAttempts

  @Autowired
  Keyspace keyspace

  @Autowired
  ObjectMapper objectMapper

  @PostConstruct
  void init() {
    try {
      runQuery(TEST_QUERY)
    } catch (BadRequestException ignored) {
      keyspace.prepareQuery(CF_APPLICATION).withCql(
          '''CREATE TABLE application(
                name varchar,
                description varchar,
                email varchar,
                updatets varchar,
                createts varchar,
                accounts list<text>,
                details_json varchar,
                version int,
                PRIMARY KEY (name)
             ) with compression={};''').execute()
    }

    try {
      // TODO: migrate schema on the fly, remove any time after 2/1/16.
      runQuery("select details_json from application")
    } catch (BadRequestException ignored) {
      runQuery("ALTER TABLE application ADD details_json varchar")
      runQuery("ALTER TABLE application ADD version int")
      all().each { Application application ->
        application.platformHealthOnly = new Boolean(application.details().platformHealthOnly)
        application.platformHealthOnlyShowOverride = new Boolean(application.details().platformHealthOnlyShowOverride)
        update(application.name, application)
      }
    }
  }

  @Override
  Application findByName(String name) throws NotFoundException {
    def applications = unmarshallResults(runQuery("select * from application where name='${name}';"))

    if (!applications) {
      throw new NotFoundException("No Application found by name of ${name}")
    }

    return applications[0]
  }

  @Override
  Set<Application> all() {
    def applications = unmarshallResults(runQuery('SELECT * FROM application;'))
    if (!applications) {
      throw new NotFoundException("No applications available")
    }
    return applications
  }

  @Override
  Application create(String id, Application application) {
    if (!application.createTs) {
      // support the migration use-case where we don't want to arbitrarily reset an applications createTs.
      application.createTs = System.currentTimeMillis() as String
    }

    application.name = id
    runQuery(buildInsertQuery(application))
    return application
  }

  @Override
  void update(String id, Application application) {
    application.name = id
    application.updateTs = System.currentTimeMillis() as String

    runQuery(buildInsertQuery(application))
  }

  @Override
  void delete(String id) {
    runQuery("DELETE FROM application WHERE name = '${id}';")
  }

  @Override
  boolean isHealthy() {
    try {
      runQuery(TEST_QUERY)
      return true
    } catch (BadRequestException ignored) {
      return false
    }
  }

  @Override
  Set<Application> search(Map<String, String> attributes) {
    def searchableApplications = all()
    attributes = attributes.collect { k,v -> [k.toLowerCase(), v] }.collectEntries()

    if (attributes["accounts"]) {
      // CQL 3.0/Cassandra 1.2 doesn't support the filtering of collections (3.1/2.0+ do)
      def accounts = attributes["accounts"].split(",").collect { it.trim().toLowerCase() }
      searchableApplications = searchableApplications.findAll {
        def applicationAccounts = (it.accounts ?: "").split(",").collect { it.trim().toLowerCase() }
        return applicationAccounts.containsAll(accounts)
      }

      // remove the 'accounts' search attribute so it's not picked up again in the field-level filtering below
      attributes.remove("accounts")
    }

    // filtering vs. querying to achieve case-insensitivity without using an additional column (small data set)
    def items = searchableApplications.findAll { app ->
      def result = true
      attributes.each { k, v ->
        if (!v) {
          return
        }
        if (!app.hasProperty(k) && !app.details().containsKey(k)) {
          result = false
        }
        def appVal = app.hasProperty(k) ? app[k] : app.details()[k] ?: ""
        if (!appVal.toString().toLowerCase().contains(v.toLowerCase())) {
          result = false
        }
      }
      return result
    } as Set

    if (!items) {
      throw new NotFoundException("No Application found for search criteria $attributes")
    }

    return items
  }

  void truncate() {
    keyspace.truncateColumnFamily(CF_NAME)
  }

  private List<Application> unmarshallResults(OperationResult result) {
    return result.result.rows.collect { Row<Integer, String> row ->
      ColumnList columns = row.columns

      def getStringValue = { String columnName ->
        def column = columns.getColumnByName(columnName)
        return column.hasValue() ? (column.stringValue ?: null) : null
      }

      def details = [:]
      def detailsJson = columns.getColumnByName("details_json")
      if (detailsJson?.hasValue()) {
        details = objectMapper.readValue(detailsJson.stringValue ?: null, Map)
      } else {
        // TODO: Remove this else block after 2/1/16
        def detailsColumn = columns.getColumnByName("details")
        if (detailsColumn?.hasValue()) {
          details.putAll(detailsColumn.getValue(mapSerializer))
        }
      }

      def accountsColumn = columns.getColumnByName("accounts")
      def accounts = []
      if (accountsColumn?.hasValue()) {
        accounts.addAll(accountsColumn.getValue(listSerializer))
      }

      return new Application(
          name: getStringValue('name'),
          description: getStringValue('description'),
          email: getStringValue('email'),
          accounts: accounts ? accounts.join(",") : null,
          updateTs: getStringValue('updatets'),
          createTs: getStringValue('createts'),
          details: details
      )
    }
  }

  private OperationResult runQuery(String query) {
    return keyspace
        .prepareQuery(CF_APPLICATION)
        .withRetryPolicy(new ExponentialBackoff(exponentialBaseSleepTimeMs, exponentialMaxAttempts))
        .withCql(query)
        .execute()
  }

  private OperationResult runQuery(Query query) {
    def preparedQuery = keyspace
        .prepareQuery(CF_APPLICATION)
        .withRetryPolicy(new ExponentialBackoff(exponentialBaseSleepTimeMs, exponentialMaxAttempts))
        .withCql(query.cql)
        .asPreparedStatement()
    query.values.each {
      if (it instanceof String) {
        preparedQuery = preparedQuery.withStringValue(it)
      } else if (it instanceof Integer) {
        preparedQuery = preparedQuery.withIntegerValue(it)
      } else if (it instanceof Map) {
        preparedQuery = preparedQuery.withValue(mapSerializer.toByteBuffer(it))
      } else if (it instanceof List) {
        preparedQuery = preparedQuery.withValue(listSerializer.toByteBuffer(it))
      }
    }

    return preparedQuery.execute()
  }

  private Query buildInsertQuery(Application application) {
    def keys = ["version"]
    def values = [VERSION]
    application.getPersistedProperties().each { key, value ->
      switch (key) {
        case "accounts":
          keys << key
          values << (value ? value.split(",").collect { it.trim().toLowerCase() } : [])
          break
        case "details":
          keys << "details_json"
          values << objectMapper.writeValueAsString(value) ?: null
          break
        default:
          keys << key
          values << (value ?: "")
      }
    }

    def cql = """INSERT INTO application (${keys.join(",")}) VALUES (${keys.collect { "?" }.join(",")});"""
    return new Query(cql: cql, values: values)
  }

  private static class Query {
    String cql
    List<Object> values
  }
}
