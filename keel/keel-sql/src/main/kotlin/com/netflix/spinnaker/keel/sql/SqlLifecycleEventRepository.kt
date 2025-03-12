package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.JsonMappingException
import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.lifecycle.LifecycleStep
import com.netflix.spinnaker.keel.lifecycle.StartMonitoringEvent
import com.netflix.spinnaker.keel.lifecycle.isEndingStatus
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.LIFECYCLE_EVENT
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.select
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Duration

class SqlLifecycleEventRepository(
  private val clock: Clock,
  private val jooq: DSLContext,
  private val sqlRetry: SqlRetry,
  private val spectator: Registry,
  private val publisher: ApplicationEventPublisher
) : LifecycleEventRepository {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun saveEvent(event: LifecycleEvent): String {
    val timestamp = event.timestamp ?: clock.instant()
    var eventUid = ULID().nextULID(clock.millis())
    sqlRetry.withRetry(WRITE) {
      jooq.transaction { config ->
        val txn = DSL.using(config)
        // if event exists, only update timestamp
        var eventExists = false
        txn.select(LIFECYCLE_EVENT.UID, LIFECYCLE_EVENT.JSON)
          .from(LIFECYCLE_EVENT)
          .where(LIFECYCLE_EVENT.SCOPE.eq(event.scope.name))
          .and(LIFECYCLE_EVENT.DELIVERY_CONFIG_NAME.eq(event.deliveryConfigName))
          .and(LIFECYCLE_EVENT.ARTIFACT_REFERENCE.eq(event.artifactReference))
          .and(LIFECYCLE_EVENT.ARTIFACT_VERSION.eq(event.artifactVersion))
          .and(LIFECYCLE_EVENT.TYPE.eq(event.type))
          .and(LIFECYCLE_EVENT.ID.eq(event.id))
          .and(LIFECYCLE_EVENT.STATUS.eq(event.status))
          .limit(1)
          .fetch()
          .firstOrNull()
          ?.let { (uid, savedEvent) ->
            eventExists = true
            eventUid = uid
            try {
              if (event == savedEvent.copy(timestamp = event.timestamp)) {
                // events are the same except time
                txn.update(LIFECYCLE_EVENT)
                  .set(LIFECYCLE_EVENT.TIMESTAMP, timestamp)
                  .where(LIFECYCLE_EVENT.UID.eq(uid))
                  .execute()
              }
            } catch (e: JsonMappingException) {
              // ignore existing event with incorrect serialization, just store a new one.
            }
          }

        if (!eventExists) {
          txn.insertInto(LIFECYCLE_EVENT)
            .set(LIFECYCLE_EVENT.UID, eventUid)
            .set(LIFECYCLE_EVENT.SCOPE, event.scope.name)
            .set(LIFECYCLE_EVENT.DELIVERY_CONFIG_NAME, event.deliveryConfigName)
            .set(LIFECYCLE_EVENT.ARTIFACT_REFERENCE, event.artifactReference)
            .set(LIFECYCLE_EVENT.ARTIFACT_VERSION, event.artifactVersion)
            .set(LIFECYCLE_EVENT.TYPE, event.type)
            .set(LIFECYCLE_EVENT.ID, event.id)
            .set(LIFECYCLE_EVENT.STATUS, event.status)
            .set(LIFECYCLE_EVENT.TIMESTAMP, timestamp)
            .set(LIFECYCLE_EVENT.JSON, event)
            .execute()
        }
      }
    }
    return eventUid
  }

  private val LifecycleEvent.artifactUid
    get() = select(DELIVERY_ARTIFACT.UID)
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
      .and(DELIVERY_ARTIFACT.NAME.eq(artifactReference))

  override fun getEvents(
    artifact: DeliveryArtifact,
    artifactVersion: String
  ): List<LifecycleEvent> =
    sqlRetry.withRetry(READ) {
      jooq.select(LIFECYCLE_EVENT.JSON, LIFECYCLE_EVENT.TIMESTAMP)
        .from(LIFECYCLE_EVENT)
        .where(LIFECYCLE_EVENT.DELIVERY_CONFIG_NAME.eq(artifact.deliveryConfigName))
        .and(LIFECYCLE_EVENT.ARTIFACT_REFERENCE.eq(artifact.reference))
        .and(LIFECYCLE_EVENT.ARTIFACT_VERSION.eq(artifactVersion))
        .orderBy(LIFECYCLE_EVENT.TIMESTAMP.asc()) // oldest first
        .fetch()
        .map { (event, timestamp) ->
          event.copy(timestamp = timestamp)
        }.filterNotNull()
    }

  fun getEvents(
    artifact: DeliveryArtifact
  ): List<LifecycleEvent> =
  sqlRetry.withRetry(READ) {
    jooq.select(LIFECYCLE_EVENT.JSON, LIFECYCLE_EVENT.TIMESTAMP)
      .from(LIFECYCLE_EVENT)
      .where(LIFECYCLE_EVENT.DELIVERY_CONFIG_NAME.eq(artifact.deliveryConfigName))
      .and(LIFECYCLE_EVENT.ARTIFACT_REFERENCE.eq(artifact.reference))
      .orderBy(LIFECYCLE_EVENT.TIMESTAMP.asc()) // oldest first
      .fetch()
      .map { (event, timestamp) ->
        event.copy(timestamp = timestamp)
      }.filterNotNull()
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
    val steps = calculateSteps(events)
    spectator.timer(
      LIFECYCLE_STEP_CALCULATION_DURATION_ID,
      listOf(BasicTag("artifactRef", "${artifact.deliveryConfigName}:${artifact.reference}"))
    ).record(Duration.between(startTime, clock.instant()))
    return steps
  }

  private fun calculateSteps(events: List<LifecycleEvent>): List<LifecycleStep> {
    val steps: MutableList<LifecycleStep> = mutableListOf()

    // associateBy overwrites values when presented with duplicate keys
    // get first and last event by sorting both ways
    val firstEventById = events.sortedByDescending { it.timestamp }.associateBy { it.id }
    val lastEventById = events.associateBy { it.id }
    // sometimes an ending event will be out of order, so we need to grab those events
    val endingEventsById = events.filter { it.status.isEndingStatus() }.associateBy { it.id }

    firstEventById.forEach { (id, event) ->
      // call this for each event id in case we missed monitoring the initial event.
      retriggerMonitoring(events.filter { it.id == id })

      var step = event.toStep()
      val lastEvent = endingEventsById[id]
        ?: lastEventById[id] // if there's an ending status, use that as the last event
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
      }

      // only add the step if we have a valid link
      if (step.link?.startsWith("http") == true) {
        steps.add(step)
      }
    }

    return steps.toList().sortedBy { it.startedAt }
  }

  override fun getSteps(artifact: DeliveryArtifact): List<LifecycleStep> {
    val startTime = clock.instant()
    val events = getEvents(artifact)
    val steps = calculateSteps(events)

    spectator.timer(
      LIFECYCLE_STEP_CALCULATION_ALL_DURATION_ID,
      listOf(BasicTag("artifactRef", "${artifact.deliveryConfigName}:${artifact.reference}"))
    ).record(Duration.between(startTime, clock.instant()))
    return steps
  }

  /**
   * Publishes a [StartMonitoringEvent] if we only have one event for
   * an id and if the event is older than 5 minutes
   * because we dropped a fair amount of monitoring of events.
   *
   * Update 3/2020: This is extremely useful for local testing, so it stays.
   */
  private fun retriggerMonitoring(events: List<LifecycleEvent>) {
    if (events.size != 1) {
      // we don't need to kick start monitoring if there's more than one event
      // this is just for the case that we never started monitoring.
      return
    }
    val event = events.first()
    val now = clock.instant()
    val eventTimestamp = event.timestamp ?: return
    val age = Duration.between(eventTimestamp, now)

    if (event.startMonitoring && !event.status.isEndingStatus() && age > Duration.ofMinutes(5)) {
      // since there is only one event and it's older than 5 minutes,
      // recheck it in case we missed monitoring it.
      log.info("Re-triggering monitoring for event $event")
      val uid = event.getUid()
      publisher.publishEvent(StartMonitoringEvent(uid, event))
    }
  }

  private fun LifecycleEvent.getUid() =
    sqlRetry.withRetry(READ) {
      jooq.select(LIFECYCLE_EVENT.UID)
        .from(LIFECYCLE_EVENT)
        .where(LIFECYCLE_EVENT.SCOPE.eq(scope.name))
        .and(LIFECYCLE_EVENT.TYPE.eq(type))
        .and(LIFECYCLE_EVENT.DELIVERY_CONFIG_NAME.eq(deliveryConfigName))
        .and(LIFECYCLE_EVENT.ARTIFACT_REFERENCE.eq(artifactReference))
        .and(LIFECYCLE_EVENT.ARTIFACT_VERSION.eq(artifactVersion))
        .and(LIFECYCLE_EVENT.ID.eq(id))
        .and(LIFECYCLE_EVENT.STATUS.eq(status))
        .fetch(LIFECYCLE_EVENT.UID)
        .first()
  }

  private val LIFECYCLE_STEP_CALCULATION_DURATION_ID = "keel.lifecycle.step.calculation.duration"
  private val LIFECYCLE_STEP_CALCULATION_ALL_DURATION_ID = "keel.lifecycle.step.calculation.all.duration"
}
