/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.q.migration

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.orca.q.AbortStage
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.CancelStage
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.CompleteStage
import com.netflix.spinnaker.orca.q.CompleteTask
import com.netflix.spinnaker.orca.q.ContinueParentStage
import com.netflix.spinnaker.orca.q.InvalidExecutionId
import com.netflix.spinnaker.orca.q.InvalidStageId
import com.netflix.spinnaker.orca.q.InvalidTaskId
import com.netflix.spinnaker.orca.q.InvalidTaskType
import com.netflix.spinnaker.orca.q.NoDownstreamTasks
import com.netflix.spinnaker.orca.q.PauseStage
import com.netflix.spinnaker.orca.q.PauseTask
import com.netflix.spinnaker.orca.q.RescheduleExecution
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.ResumeExecution
import com.netflix.spinnaker.orca.q.ResumeStage
import com.netflix.spinnaker.orca.q.ResumeTask
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.SkipStage
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.StartTask
import com.netflix.spinnaker.orca.q.TotalThrottleTimeAttribute
import com.netflix.spinnaker.orca.q.handler.DeadMessageAttribute
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.JSON_NAME_PROPERTY
import com.netflix.spinnaker.q.MaxAttemptsAttribute
import com.netflix.spinnaker.q.migration.SerializationMigrator
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

@Component
@ConditionalOnExpression("\${queue.migration.minimalClassTypeInfoEnabled:true}")
class MinimalClassTypeInfoSerializationMigrator : SerializationMigrator {

  private val log = LoggerFactory.getLogger(javaClass)

  private val migrated = AtomicInteger(0)
  private var lastReport: LocalTime? = null

  init {
    log.info("${javaClass.simpleName} enabled")
  }

  override fun migrate(json: MutableMap<String, Any?>): MutableMap<String, Any?> {
    migrateMinimalClass(json)

    if (json.containsKey("attributes") && json["attributes"] is List<*>) {
      (json["attributes"] as List<*>)
        .filterIsInstance<MutableMap<String, Any?>>()
        .forEach { migrateMinimalClass(it) }
    }

    return json
  }

  private fun migrateMinimalClass(json: MutableMap<String, Any?>) {
    val minimalClass = getClassAnnotation(json)
    if (minimalClass != null) {
      val cls = MAPPING[minimalClass]
      if (cls == null) {
        log.error("Could not find minimal class mapping: $minimalClass")
        return
      }

      val typeInfoName = cls.annotations.filterIsInstance<JsonTypeName>().firstOrNull()
      if (typeInfoName == null) {
        log.error("Class ${cls.simpleName} does not have JsonTypeName: Cannot migrate message")
        return
      }

      json.apply {
        remove("@c")
        remove("@class")
        put(JSON_NAME_PROPERTY, typeInfoName.value)
      }

      migrated.incrementAndGet()
    }
  }

  private fun getClassAnnotation(json: MutableMap<String, Any?>) =
    (json["@c"] ?: json["@class"]) as String?

  @Scheduled(fixedDelay = 60_000)
  override fun report() {
    log.info(
      "Migrated {} objects since {}",
      migrated.get(),
      if (lastReport == null) "startup" else lastReport!!.format(DateTimeFormatter.ISO_TIME)
    )

    migrated.set(0)
    lastReport = LocalTime.now()
  }

  companion object {
    private val MAPPING = mapOf<String, Class<*>>(
      ".StartTask" to StartTask::class.java,
      ".CompleteTask" to CompleteTask::class.java,
      ".PauseTask" to PauseTask::class.java,
      ".ResumeTask" to ResumeTask::class.java,
      ".RunTask" to RunTask::class.java,
      ".StartStage" to StartStage::class.java,
      ".ContinueParentStage" to ContinueParentStage::class.java,
      ".CompleteStage" to CompleteStage::class.java,
      ".SkipStage" to SkipStage::class.java,
      ".AbortStage" to AbortStage::class.java,
      ".PauseStage" to PauseStage::class.java,
      ".RestartStage" to RestartStage::class.java,
      ".ResumeStage" to ResumeStage::class.java,
      ".CancelStage" to CancelStage::class.java,
      ".StartExecution" to StartExecution::class.java,
      ".RescheduleExecution" to RescheduleExecution::class.java,
      ".CompleteExecution" to CompleteExecution::class.java,
      ".ResumeExecution" to ResumeExecution::class.java,
      ".CancelExecution" to CancelExecution::class.java,
      ".InvalidExecutionId" to InvalidExecutionId::class.java,
      ".InvalidStageId" to InvalidStageId::class.java,
      ".InvalidTaskId" to InvalidTaskId::class.java,
      ".InvalidTaskType" to InvalidTaskType::class.java,
      ".NoDownstreamTasks" to NoDownstreamTasks::class.java,
      ".TotalThrottleTimeAttribute" to TotalThrottleTimeAttribute::class.java,
      ".handler.DeadMessageAttribute" to DeadMessageAttribute::class.java,
      ".MaxAttemptsAttribute" to MaxAttemptsAttribute::class.java,
      ".AttemptsAttribute" to AttemptsAttribute::class.java
    )
  }
}
