package com.netflix.spinnaker.keel.orca

import java.time.Duration

/**
 * For inclusion directly in the stage context of Orca tasks
 */
fun restrictedExecutionWindow(
  startHour: Int,
  endHour: Int,
  days: Collection<Int>? = null
): Map<String, Any?> =
  mapOf(
    "restrictExecutionDuringTimeWindow" to true,
    "restrictedExecutionWindow" to mapOf<String, Any>(
      "days" to when (days) {
        null -> (1..7).toList()
        else -> days.toList()
      },
      "whitelist" to listOf(
        mapOf(
          "startHour" to startHour,
          "startMin" to 0,
          "endHour" to endHour,
          "endMin" to 0
        )
      )
    )
  )

fun waitStage(wait: Duration, startingRefId: Int): Map<String, Any?> =
  mapOf(
    "refId" to (startingRefId + 1).toString(),
    "requisiteStageRefIds" to when (startingRefId) {
      0 -> emptyList()
      else -> listOf(startingRefId.toString())
    },
    "type" to "wait",
    "waitTime" to wait.seconds
  )

fun dependsOn(
  id: String,
  type: String = "orchestration",
  startingRefId: Int = 0
): Map<String, Any?> =
  mapOf(
    "refId" to (startingRefId + 1).toString(),
    "requisiteStageRefIds" to when (startingRefId) {
      0 -> emptyList()
      else -> listOf(startingRefId.toString())
    },
    "type" to "dependsOnExecution",
    "executionId" to id,
    "executionType" to type
  )
