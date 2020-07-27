/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.sql.event

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.event.Aggregate
import com.netflix.spinnaker.clouddriver.event.CompositeSpinnakerEvent
import com.netflix.spinnaker.clouddriver.event.EventMetadata
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent
import com.netflix.spinnaker.clouddriver.event.exceptions.InvalidEventTypeException
import org.jooq.Condition
import org.jooq.Record
import org.jooq.Select
import org.jooq.SelectConditionStep
import org.jooq.SelectJoinStep
import org.jooq.impl.DSL.currentTimestamp

/**
 * Adds an arbitrary number of [conditions] to a query joined by `AND` operator.
 */
internal fun <R : Record> SelectJoinStep<R>.withConditions(conditions: List<Condition>): SelectConditionStep<R> {
  return if (conditions.isNotEmpty()) this.where(
    conditions.reduce { acc, condition -> acc.and(condition) }
  ) else {
    where("1=1")
  }
}

/**
 * Internal model of [Aggregate].
 */
internal class SqlAggregate(
  val model: Aggregate,
  val token: String
)

/**
 * Runs [this] as a "select one" query and returns a single [SqlAggregate].
 * It is assumed the underlying [Aggregate] exists.
 */
internal fun Select<out Record>.fetchAggregates(): List<SqlAggregate> =
  fetch().intoResultSet().let { rs ->
    mutableListOf<SqlAggregate>().apply {
      while (rs.next()) {
        add(
          SqlAggregate(
            model = Aggregate(
              type = rs.getString("aggregate_type"),
              id = rs.getString("aggregate_id"),
              version = rs.getLong("version")
            ),
            token = rs.getString("token")
          )
        )
      }
    }
  }

/**
 * Converts a [SpinnakerEvent] to a SQL event row. The values are ordered the same as the schema's columns.
 */
internal fun SpinnakerEvent.toSqlValues(objectMapper: ObjectMapper): Collection<Any> = listOf(
  getMetadata().id,
  getMetadata().aggregateType,
  getMetadata().aggregateId,
  getMetadata().sequence,
  getMetadata().originatingVersion,
  currentTimestamp(),
  objectMapper.writeValueAsString(getMetadata()),
  // TODO(rz): optimize
  objectMapper.writeValueAsString(this)
)

/**
 * Executes a SQL select query and converts the ResultSet into a list of [SpinnakerEvent].
 */
internal fun Select<out Record>.fetchEvents(objectMapper: ObjectMapper): List<SpinnakerEvent> =
  fetch().intoResultSet().let { rs ->
    mutableListOf<SpinnakerEvent>().apply {
      while (rs.next()) {
        try {
          val event = objectMapper.readValue(rs.getString("data"), SpinnakerEvent::class.java).apply {
            setMetadata(objectMapper.readValue(rs.getString("metadata"), EventMetadata::class.java))
          }
          if (event is CompositeSpinnakerEvent) {
            event.getComposedEvents().forEach {
              it.setMetadata(event.getMetadata().copy(id = "N/A", sequence = -1))
            }
          }
          add(event)
        } catch (e: JsonProcessingException) {
          throw InvalidEventTypeException(e)
        }
      }
    }
  }
