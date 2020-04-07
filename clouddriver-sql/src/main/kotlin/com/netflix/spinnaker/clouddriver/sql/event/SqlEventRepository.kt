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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.event.Aggregate
import com.netflix.spinnaker.clouddriver.event.CompositeSpinnakerEvent
import com.netflix.spinnaker.clouddriver.event.EventMetadata
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent
import com.netflix.spinnaker.clouddriver.event.exceptions.AggregateChangeRejectedException
import com.netflix.spinnaker.clouddriver.event.exceptions.DuplicateEventAggregateException
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository.ListAggregatesCriteria
import com.netflix.spinnaker.clouddriver.sql.transactional
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.kork.version.ServiceVersion
import de.huxhorn.sulky.ulid.ULID
import java.sql.SQLIntegrityConstraintViolationException
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL.currentTimestamp
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

class SqlEventRepository(
  private val jooq: DSLContext,
  private val serviceVersion: ServiceVersion,
  private val objectMapper: ObjectMapper,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val registry: Registry
) : EventRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val eventCountId = registry.createId("eventing.events")
  private val eventErrorCountId = registry.createId("eventing.errors")

  override fun save(
    aggregateType: String,
    aggregateId: String,
    originatingVersion: Long,
    newEvents: List<SpinnakerEvent>
  ) {
    val eventNames = newEvents.joinToString { it.javaClass.simpleName }
    log.debug("Saving $aggregateType/$aggregateId expecting version $originatingVersion with [$eventNames]")

    val aggregateCondition = field("aggregate_type").eq(aggregateType)
      .and(field("aggregate_id").eq(aggregateId))

    try {
      withPool(POOL_NAME) {
        jooq.transactional { ctx ->
          // Get or create the aggregate and immediately assert that this save operation is being committed against the
          // most recent aggregate state.
          val aggregate = ctx.maybeGetAggregate(aggregateCondition) ?: {
            if (originatingVersion != 0L) {
              // The aggregate doesn't exist and we're already expecting a non-zero version.
              throw AggregateChangeRejectedException(-1, originatingVersion)
            }

            // The aggregate doesn't exist yet, so we'll go ahead and seed it immediately.
            val initialAggregate = mapOf(
              field("aggregate_type") to aggregateType,
              field("aggregate_id") to aggregateId,
              field("token") to ulid.nextULID(),
              field("version") to 0
            )

            try {
              ctx.insertInto(AGGREGATES_TABLE)
                .columns(initialAggregate.keys)
                .values(initialAggregate.values)
                .execute()
            } catch (e: SQLIntegrityConstraintViolationException) {
              // In the event that two requests are made at the same time to create a new aggregate (via two diff
              // clouddriver instances), catch the exception and bubble it up as a duplicate exception so that it
              // may be processed in an idempotent way, rather than causing an error.
              //
              // This is preferential to going back to the database to load the existing aggregate record, since we
              // already know the aggregate version will not match the originating version expected from this process
              // and would fail just below anyway.
              throw DuplicateEventAggregateException(e)
            }

            Aggregate(aggregateType, aggregateId, 0)
          }()

          if (aggregate.version != originatingVersion) {
            throw AggregateChangeRejectedException(aggregate.version, originatingVersion)
          }

          // Events have their own auto-incrementing sequence within an aggregate; so we need to get the last sequence
          // and generate from there.
          val lastSequence = ctx.select(max(field("sequence"))).from(EVENTS_TABLE)
            .where(aggregateCondition)
            .limit(1)
            .fetchOne(0, Long::class.java)

          log.debug("Last event sequence number is $lastSequence")
          var nextSequence = lastSequence

          // Add the new events, doesn't matter what they are: At this point, they're "probably" valid, as the higher
          // libs should be validating the event payload.
          ctx.insertInto(EVENTS_TABLE)
            .columns(
              field("id"),
              field("aggregate_type"),
              field("aggregate_id"),
              field("sequence"),
              field("originating_version"),
              field("timestamp"),
              field("metadata"),
              field("data")
            )
            .let { insertValuesStep ->
              var step = insertValuesStep
              newEvents.forEach {
                nextSequence = it.initialize(aggregateType, aggregateId, originatingVersion, nextSequence)
                step = step.values(it.toSqlValues(objectMapper))
              }
              step
            }
            .execute()

          // Update the aggregates table with a new version
          ctx.update(AGGREGATES_TABLE)
            .set(field("version"), field("version", Long::class.java).add(1))
            .set(field("last_change_timestamp"), currentTimestamp())
            .where(aggregateCondition)
            .execute()

          log.debug("Event sequence number is now $nextSequence")
        }
      }
    } catch (e: AggregateChangeRejectedException) {
      registry.counter(
        eventErrorCountId
          .withTags("aggregateType", aggregateType, "exception", e.javaClass.simpleName))
        .increment()
      throw e
    } catch (e: Exception) {
      // This is totally handling it...
      registry.counter(
        eventErrorCountId
          .withTags("aggregateType", aggregateType, "exception", e.javaClass.simpleName))
        .increment()
      throw SqlEventSystemException("Failed saving new events", e)
    }

    log.debug("Saved $aggregateType/$aggregateId: [${newEvents.joinToString { it.javaClass.simpleName}}]")
    registry.counter(eventCountId.withTags("aggregateType", aggregateType)).increment(newEvents.size.toLong())

    newEvents.forEach { applicationEventPublisher.publishEvent(it) }
  }

  /**
   * Initialize the [SpinnakerEvent] lateinit properties (recursively, if necessary).
   *
   * This is a bit wonky: In the case of [ComposedSpinnakerEvent]s, we want to initialize the event so we can
   * correctly serialize it, but we don't want to increment the sequence for these events as they aren't
   * actually on the event log yet. If we're in a [ComposedSpinnakerEvent], we just provide a "-1" sequence
   * number and a real, valid sequence will be assigned if/when it gets saved to the event log.
   */
  private fun SpinnakerEvent.initialize(
    aggregateType: String,
    aggregateId: String,
    originatingVersion: Long,
    currentSequence: Long?
  ): Long? {
    var nextSequence = if (currentSequence != null) {
      currentSequence + 1
    } else {
      null
    }

    // timestamp is calculated on the SQL server
    setMetadata(EventMetadata(
      id = UUID.randomUUID().toString(),
      aggregateType = aggregateType,
      aggregateId = aggregateId,
      sequence = nextSequence ?: -1,
      originatingVersion = originatingVersion,
      serviceVersion = serviceVersion.resolve()
    ))

    if (this is CompositeSpinnakerEvent) {
      this.getComposedEvents().forEach { event ->
        // We initialize composed events with a null sequence, since they won't actually get added to the log at
        // this point; that's up to the action to either add it or not, at which point it'll get a sequence number
        event.initialize(aggregateType, aggregateId, originatingVersion, null)?.let {
          nextSequence = it
        }
      }
    }

    return nextSequence
  }

  override fun list(aggregateType: String, aggregateId: String): List<SpinnakerEvent> {
    return withPool(POOL_NAME) {
      jooq.select().from(EVENTS_TABLE)
        .where(field("aggregate_type").eq(aggregateType)
          .and(field("aggregate_id").eq(aggregateId)))
        .orderBy(field("sequence").asc())
        .fetchEvents(objectMapper)
    }
  }

  override fun listAggregates(criteria: ListAggregatesCriteria): EventRepository.ListAggregatesResult {
    // TODO(rz): validate criteria

    return withPool(POOL_NAME) {
      val conditions = mutableListOf<Condition>()
      criteria.aggregateType?.let { conditions.add(field("aggregate_type").eq(it)) }
      criteria.token?.let { conditions.add(field("token").greaterThan(it)) }

      val perPage = criteria.perPage.coerceAtMost(10_000)

      val aggregates = jooq.select().from(AGGREGATES_TABLE)
        .withConditions(conditions)
        .orderBy(field("token").asc())
        .limit(perPage)
        .fetchAggregates()

      val remaining = jooq.selectCount().from(AGGREGATES_TABLE)
        .withConditions(conditions)
        .fetchOne(0, Int::class.java) - perPage

      EventRepository.ListAggregatesResult(
        aggregates = aggregates.map { it.model },
        nextPageToken = if (remaining > 0) aggregates.lastOrNull()?.token else null
      )
    }
  }

  private fun DSLContext.maybeGetAggregate(aggregateCondition: Condition): Aggregate? {
    return select()
      .from(AGGREGATES_TABLE)
      .where(aggregateCondition)
      .limit(1)
      .fetchAggregates()
      .firstOrNull()
      ?.model
  }

  companion object {
    private val POOL_NAME = ConnectionPools.EVENTS.value
    private val AGGREGATES_TABLE = table("event_aggregates")
    private val EVENTS_TABLE = table("events")

    private val ulid = ULID()
  }
}
