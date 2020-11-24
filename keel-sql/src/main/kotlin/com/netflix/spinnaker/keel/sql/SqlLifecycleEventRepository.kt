package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.NOT_STARTED
import com.netflix.spinnaker.keel.lifecycle.LifecycleStep
import com.netflix.spinnaker.keel.lifecycle.isEndingStatus
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.LIFECYCLE_EVENT
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset.UTC

class SqlLifecycleEventRepository(
  private val clock: Clock,
  private val jooq: DSLContext,
  private val sqlRetry: SqlRetry,
  private val objectMapper: ObjectMapper,
  private val spectator: Registry
) : LifecycleEventRepository {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun saveEvent(event: LifecycleEvent) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(LIFECYCLE_EVENT)
        .set(LIFECYCLE_EVENT.UID, ULID().nextULID(clock.millis()))
        .set(LIFECYCLE_EVENT.SCOPE, event.scope.name)
        .set(LIFECYCLE_EVENT.REF, event.artifactRef)
        .set(LIFECYCLE_EVENT.ARTIFACT_VERSION, event.artifactVersion)
        .set(LIFECYCLE_EVENT.TYPE, event.type.name)
        .set(LIFECYCLE_EVENT.ID, event.id)
        .set(LIFECYCLE_EVENT.STATUS, event.status.name)
        .set(LIFECYCLE_EVENT.TIMESTAMP, clock.timestamp())
        .set(LIFECYCLE_EVENT.JSON, objectMapper.writeValueAsString(event))
        .execute()
    }
  }

  override fun getEvents(artifact: DeliveryArtifact, artifactVersion: String): List<LifecycleEvent> {
    return sqlRetry.withRetry(READ) {
      jooq.select(LIFECYCLE_EVENT.JSON, LIFECYCLE_EVENT.TIMESTAMP)
        .from(LIFECYCLE_EVENT)
        .where(LIFECYCLE_EVENT.REF.eq(artifact.toLifecycleRef()))
        .and(LIFECYCLE_EVENT.ARTIFACT_VERSION.eq(artifactVersion))
        .orderBy(LIFECYCLE_EVENT.TIMESTAMP.asc()) // oldest first
        .fetch()
        .map { (json, timestamp) ->
          try {
            val event = objectMapper.readValue<LifecycleEvent>(json)
            event.copy(timestamp = timestamp.toInstant(UTC))
          } catch (e: JsonMappingException) {
            log.error("Exception encountered parsing lifecycle event $json", e)
            // returning null here so bad serialization doesn't break the whole view,
            // it just removes the events
            null
          }
        }.filterNotNull()
    }
  }

  /**
   * For each id, look at the first and last event to create a current status
   *  with a start time and end time (end time is added only if the step is "done".
   *
   * @return a list of steps sorted in ascending order (oldest start time first)
   *
   * This function is not optimized for reads, it recalculates the summaries on the fly.
   * To optimize, we could change how we write events so that we don't write as many duplicate
   * events while the event is being monitored (RUNNING status). We also store this record instead
   * of the list of individual events. We could also cache finished steps.
   */
  override fun getSteps(artifact: DeliveryArtifact, artifactVersion: String): List<LifecycleStep> {
    val startTime = clock.instant()
    val events = getEvents(artifact, artifactVersion)
    val steps: MutableList<LifecycleStep> = mutableListOf()

    val firstEventById = events.filter { it.status == NOT_STARTED }.associateBy { it.id }
    val lastEventById = events.associateBy { it.id }

    if (firstEventById.size != lastEventById.size) {
      log.error("Missing a NOT_STARTED event for artifact ${artifact.toLifecycleRef()} with version $artifactVersion. " +
        "This may lead to some wonky steps or no monitoring. " +
        "firstEvents: $firstEventById, lastEvents: $lastEventById")
    }

    firstEventById.forEach { (id, event) ->
      var step = event.toStep()
      val lastEvent = lastEventById[id]
      if (lastEvent != null) {
        if (lastEvent.status.isEndingStatus()) {
          step = step.copy(completedAt = lastEvent.timestamp)
        }
        lastEvent.text?.let {
          step = step.copy(text = it)
        }
        lastEvent.link?.let {
          step = step.copy(link = it)
        }
        step = step.copy(status = lastEvent.status)
      } else {
        log.error("Somehow we have a NOT_STARTED event but no last event for $event. Not sure how this could happen.")
      }
      steps.add(step)
    }

    spectator.timer(
      LIFECYCLE_STEP_CALCULATION_DURATION_ID,
      listOf(BasicTag("artifactRef", artifact.toLifecycleRef()))
    ).record(Duration.between(startTime, clock.instant()))

    return steps.toList().sortedBy { it.startedAt }
  }

  private val LIFECYCLE_STEP_CALCULATION_DURATION_ID = "keel.lifecycle.step.calculation.duration"
}
