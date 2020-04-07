/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.event.persistence

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.event.Aggregate
import com.netflix.spinnaker.clouddriver.event.EventMetadata
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent
import com.netflix.spinnaker.clouddriver.event.config.MemoryEventRepositoryConfigProperties
import com.netflix.spinnaker.clouddriver.event.exceptions.AggregateChangeRejectedException
import com.netflix.spinnaker.kork.exceptions.SystemException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled

/**
 * An in-memory only [EventRepository]. This implementation should only be used for testing.
 */
class InMemoryEventRepository(
  private val config: MemoryEventRepositoryConfigProperties,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val registry: Registry
) : EventRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val aggregateCountId = registry.createId("eventing.aggregates")
  private val aggregateWriteCountId = registry.createId("eventing.aggregates.writes")
  private val aggregateReadCountId = registry.createId("eventing.aggregates.reads")
  private val eventCountId = registry.createId("eventing.events")
  private val eventWriteCountId = registry.createId("eventing.events.writes")
  private val eventReadCountId = registry.createId("eventing.events.reads")

  private val events: MutableMap<Aggregate, MutableList<SpinnakerEvent>> = ConcurrentHashMap()

  override fun save(
    aggregateType: String,
    aggregateId: String,
    originatingVersion: Long,
    newEvents: List<SpinnakerEvent>
  ) {
    registry.counter(aggregateWriteCountId).increment()

    val aggregate = getAggregate(aggregateType, aggregateId)

    if (aggregate.version != originatingVersion) {
      // If this is being thrown, ensure that the originating process is retried on the latest aggregate version
      // by re-reading the newEvents list.
      throw AggregateChangeRejectedException(aggregate.version, originatingVersion)
    }

    events.getOrPut(aggregate) { mutableListOf() }.let { aggregateEvents ->
      val currentSequence = aggregateEvents.map { it.getMetadata().sequence }.max() ?: 0

      newEvents.forEachIndexed { index, newEvent ->
        // TODO(rz): Plugin more metadata (provenance, serviceVersion, etc)
        newEvent.setMetadata(EventMetadata(
          id = UUID.randomUUID().toString(),
          aggregateType = aggregateType,
          aggregateId = aggregateId,
          sequence = currentSequence + (index + 1),
          originatingVersion = originatingVersion
        ))
      }

      registry.counter(eventWriteCountId).increment(newEvents.size.toLong())
      aggregateEvents.addAll(newEvents)
      aggregate.version = aggregate.version + 1
    }

    log.debug("Saved $aggregateType/$aggregateId@${aggregate.version}: " +
      "[${newEvents.joinToString(",") { it.javaClass.simpleName }}]")

    newEvents.forEach { applicationEventPublisher.publishEvent(it) }
  }

  override fun list(aggregateType: String, aggregateId: String): List<SpinnakerEvent> {
    registry.counter(eventReadCountId).increment()

    return getAggregate(aggregateType, aggregateId)
      .let {
        events[it]?.toList()
      }
      ?: throw MissingAggregateEventsException(aggregateType, aggregateId)
  }

  override fun listAggregates(criteria: EventRepository.ListAggregatesCriteria): EventRepository.ListAggregatesResult {
    val aggregates = events.keys

    val result = aggregates.toList()
      .let { list ->
        criteria.aggregateType?.let { requiredType -> list.filter { it.type == requiredType } } ?: list
      }
      .let { list ->
        criteria.token?.let { nextPageToken ->
          val start = list.indexOf(list.find { "${it.type}/${it.id}" == nextPageToken })
          val end = (start + criteria.perPage).let {
            if (it > list.size - 1) {
              list.size
            } else {
              criteria.perPage
            }
          }
          list.subList(start, end)
        } ?: list
      }

    return EventRepository.ListAggregatesResult(
      aggregates = result,
      nextPageToken = result.lastOrNull()?.let { "${it.type}/${it.id}" }
    )
  }

  private fun getAggregate(aggregateType: String, aggregateId: String): Aggregate {
    registry.counter(aggregateReadCountId).increment()

    val aggregate = Aggregate(
      aggregateType,
      aggregateId,
      0L
    )
    events.putIfAbsent(aggregate, mutableListOf())
    return events.keys.first { it == aggregate }
  }

  @Scheduled(fixedDelayString = "\${spinnaker.clouddriver.eventing.memory-repository.cleanup-job-delay-ms:60000}")
  private fun cleanup() {
    registry.counter(eventReadCountId).increment()

    config.maxAggregateAgeMs
      ?.let { Duration.ofMillis(it) }
      ?.let { maxAge ->
        val horizon = Instant.now().minus(maxAge)
        log.info("Cleaning up aggregates last updated earlier than $maxAge ($horizon)")
        events.entries
          .filter { it.value.any { event -> event.getMetadata().timestamp.isBefore(horizon) } }
          .map { it.key }
          .forEach {
            log.trace("Cleaning up $it")
            events.remove(it)
          }
      }

    config.maxAggregatesCount
      ?.let { maxCount ->
        log.info("Cleaning up aggregates to max $maxCount items, pruning by earliest updated")
        events.entries
          // Flatten into pairs of List<Aggregate, SpinnakerEvent>
          .flatMap { entry ->
            entry.value.map { Pair(entry.key, it) }
          }
          .sortedBy { it.second.getMetadata().timestamp }
          .subList(0, max(events.size - maxCount, 0))
          .forEach {
            log.trace("Cleaning up ${it.first}")
            events.remove(it.first)
          }
      }
  }

  @Scheduled(fixedRate = 1_000)
  private fun recordMetrics() {
    registry.gauge(aggregateCountId).set(events.size.toDouble())
    registry.gauge(eventCountId).set(events.flatMap { it.value }.size.toDouble())
  }

  inner class MissingAggregateEventsException(aggregateType: String, aggregateId: String) : SystemException(
    "Aggregate $aggregateType/$aggregateId is missing its internal events list store"
  )
}
