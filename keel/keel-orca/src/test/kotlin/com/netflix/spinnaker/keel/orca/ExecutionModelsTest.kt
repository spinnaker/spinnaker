package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Instant

class ExecutionModelsTest {
  private val mapper = configuredTestObjectMapper()

  @Test
  fun `TaskRefResponse extracts task ID from ref`() {
    val response = TaskRefResponse(ref = "/tasks/01ABCDEFG")
    expectThat(response.taskId).isEqualTo("01ABCDEFG")
  }

  @Test
  fun `TaskRefResponse handles ref without slash`() {
    val response = TaskRefResponse(ref = "01ABCDEFG")
    expectThat(response.taskId).isEqualTo("01ABCDEFG")
  }

  @Test
  fun `ExecutionDetailResponse deserializes with execution stages`() {
    val json = """
      {
        "id": "01ABCDEFG",
        "name": "test-execution",
        "application": "myapp",
        "buildTime": 1609459200000,
        "startTime": 1609459200000,
        "endTime": 1609459300000,
        "status": "SUCCEEDED",
        "execution": {
          "stages": [
            {
              "id": "stage-1",
              "type": "wait",
              "name": "Wait",
              "status": "SUCCEEDED"
            }
          ]
        }
      }
    """.trimIndent()

    val response: ExecutionDetailResponse = mapper.readValue(json)

    expectThat(response) {
      get { id }.isEqualTo("01ABCDEFG")
      get { name }.isEqualTo("test-execution")
      get { application }.isEqualTo("myapp")
      get { buildTime }.isEqualTo(Instant.ofEpochMilli(1609459200000))
      get { startTime }.isEqualTo(Instant.ofEpochMilli(1609459200000))
      get { endTime }.isEqualTo(Instant.ofEpochMilli(1609459300000))
      get { status }.isEqualTo(TaskStatus.SUCCEEDED)
      get { execution?.stages }.isNotNull().hasSize(1)
    }

    // Verify that stages[0] is OrcaExecutionStage (Map<String, Any>)
    val firstStage = response.execution?.stages?.get(0)
    expectThat(firstStage).isNotNull().isA<Map<String, Any>>()
    expectThat(firstStage).isA<OrcaExecutionStage>()
  }

  @Test
  fun `ExecutionDetailResponse deserializes with top-level stages for pipelines`() {
    val json = """
      {
        "id": "02HIJKLMN",
        "name": "test-pipeline",
        "application": "myapp",
        "buildTime": 1609459200000,
        "startTime": 1609459200000,
        "endTime": null,
        "status": "RUNNING",
        "stages": [
          {
            "id": "stage-1",
            "type": "deploy",
            "name": "Deploy",
            "status": "RUNNING"
          },
          {
            "id": "stage-2",
            "type": "wait",
            "name": "Wait",
            "status": "NOT_STARTED"
          }
        ]
      }
    """.trimIndent()

    val response: ExecutionDetailResponse = mapper.readValue(json)

    expectThat(response) {
      get { id }.isEqualTo("02HIJKLMN")
      get { stages }.isNotNull().hasSize(2)
      get { endTime }.isNull()
    }

    // Verify that stages[0] is OrcaExecutionStage (Map<String, Any>)
    val firstStage = response.stages?.get(0)
    expectThat(firstStage).isNotNull().isA<Map<String, Any>>()
    expectThat(firstStage).isA<OrcaExecutionStage>()
  }

  @Test
  fun `ExecutionDetailResponse handles missing optional fields`() {
    val json = """
      {
        "id": "03OPQRSTU",
        "name": "minimal-execution",
        "application": "myapp",
        "buildTime": 1609459200000,
        "startTime": null,
        "endTime": null,
        "status": "NOT_STARTED"
      }
    """.trimIndent()

    val response: ExecutionDetailResponse = mapper.readValue(json)

    expectThat(response) {
      get { id }.isEqualTo("03OPQRSTU")
      get { startTime }.isNull()
      get { endTime }.isNull()
      get { execution?.stages }.isNotNull().hasSize(0)
      get { stages }.isNotNull().hasSize(0)
      get { variables }.isNull()
    }
  }

  @Test
  fun `ExecutionDetailResponse deserializes with variables`() {
    val json = """
      {
        "id": "04VWXYZ",
        "name": "execution-with-vars",
        "application": "myapp",
        "buildTime": 1609459200000,
        "startTime": 1609459200000,
        "endTime": 1609459300000,
        "status": "SUCCEEDED",
        "variables": [
          {
            "key": "deploymentTarget",
            "value": "production"
          },
          {
            "key": "version",
            "value": "1.2.3"
          }
        ]
      }
    """.trimIndent()

    val response: ExecutionDetailResponse = mapper.readValue(json)

    expectThat(response) {
      get { variables }.isNotNull().hasSize(2)
    }

    expectThat(response.variables!![0]) {
      get { key }.isEqualTo("deploymentTarget")
      get { value }.isEqualTo("production")
    }

    expectThat(response.variables!![1]) {
      get { key }.isEqualTo("version")
      get { value }.isEqualTo("1.2.3")
    }
  }

  @Test
  fun `ExecutionDetailResponse handles malformed stages gracefully`() {
    val json = """
      {
        "id": "05ERROR",
        "name": "error-execution",
        "application": "myapp",
        "buildTime": 1609459200000,
        "startTime": 1609459200000,
        "endTime": 1609459300000,
        "status": "TERMINAL",
        "stages": "invalid-not-an-array"
      }
    """.trimIndent()

    val response: ExecutionDetailResponse = mapper.readValue(json)

    expectThat(response) {
      get { id }.isEqualTo("05ERROR")
      get { stages }.isNotNull().hasSize(0)
    }
  }

  @Test
  fun `ExecutionDetailResponse handles malformed variables gracefully`() {
    val json = """
      {
        "id": "06ERROR",
        "name": "error-execution",
        "application": "myapp",
        "buildTime": 1609459200000,
        "startTime": 1609459200000,
        "endTime": 1609459300000,
        "status": "TERMINAL",
        "variables": "invalid-not-an-array"
      }
    """.trimIndent()

    val response: ExecutionDetailResponse = mapper.readValue(json)

    expectThat(response) {
      get { id }.isEqualTo("06ERROR")
      get { variables }.isNull()
    }
  }

  @Test
  fun `OrcaExecutionStages deserializes correctly`() {
    val json = """
      {
        "stages": [
          {
            "id": "stage-1",
            "type": "wait",
            "name": "Wait"
          }
        ]
      }
    """.trimIndent()

    val response: OrcaExecutionStages = mapper.readValue(json)

    expectThat(response.stages).isNotNull().hasSize(1)

    // Verify that stages[0] is OrcaExecutionStage (Map<String, Any>)
    val firstStage = response.stages?.get(0)
    expectThat(firstStage).isNotNull().isA<Map<String, Any>>()
    expectThat(firstStage).isA<OrcaExecutionStage>()
  }

  @Test
  fun `OrcaExecutionStages handles missing stages`() {
    val json = """
      {}
    """.trimIndent()

    val response: OrcaExecutionStages = mapper.readValue(json)

    expectThat(response.stages).isNotNull().hasSize(0)
  }

  @Test
  fun `OrcaExecutionStages handles null stages`() {
    val json = """
      {
        "stages": null
      }
    """.trimIndent()

    val response: OrcaExecutionStages = mapper.readValue(json)

    expectThat(response.stages).isNotNull().hasSize(0)
  }

  @Test
  fun `KeyValuePair serializes and deserializes correctly`() {
    val pair = KeyValuePair(key = "testKey", value = "testValue")
    val json = mapper.writeValueAsString(pair)
    val deserialized: KeyValuePair = mapper.readValue(json)

    expectThat(deserialized) {
      get { key }.isEqualTo("testKey")
      get { value }.isEqualTo("testValue")
    }
  }

  @Test
  fun `KeyValuePair handles numeric values`() {
    val pair = KeyValuePair(key = "count", value = 42)
    val json = mapper.writeValueAsString(pair)
    val deserialized: KeyValuePair = mapper.readValue(json)

    expectThat(deserialized) {
      get { key }.isEqualTo("count")
      get { value }.isEqualTo(42)
    }
  }

  @Test
  fun `OrcaContext with exception deserializes correctly`() {
    val json = """
      {
        "exception": {
          "exceptionType": "com.netflix.spinnaker.orca.exceptions.TimeoutException",
          "shouldRetry": true,
          "details": {
            "stackTrace": "some stack trace",
            "error": "Timeout occurred",
            "errors": ["Error 1", "Error 2"]
          }
        }
      }
    """.trimIndent()

    val context: OrcaContext = mapper.readValue(json)

    expectThat(context.exception).isNotNull()
    expectThat(context.exception!!.exceptionType).isEqualTo("com.netflix.spinnaker.orca.exceptions.TimeoutException")
    expectThat(context.exception!!.shouldRetry).isEqualTo(true)
    expectThat(context.exception!!.details).isNotNull()
    expectThat(context.exception!!.details!!.error).isEqualTo("Timeout occurred")
  }

  @Test
  fun `OrcaContext with clouddriver exception deserializes correctly using JsonAlias`() {
    val json = """
      {
        "kato.tasks": [
          {
            "exception": {
              "cause": "Network error",
              "message": "Failed to deploy",
              "type": "CloudDriverException",
              "operation": "deploy"
            }
          }
        ]
      }
    """.trimIndent()

    val context: OrcaContext = mapper.readValue(json)

    expectThat(context.clouddriverException).isNotNull().hasSize(1)
    expectThat(context.clouddriverException!![0]).isA<Map<String, Any>>()
  }

  @Test
  fun `ExecutionDetailResponse handles malformed execution node gracefully`() {
    val json = """
      {
        "id": "07MALFORM",
        "name": "error-execution",
        "application": "myapp",
        "buildTime": 1609459200000,
        "startTime": 1609459200000,
        "endTime": 1609459300000,
        "status": "TERMINAL",
        "execution": "invalid-not-an-object"
      }
    """.trimIndent()

    val response: ExecutionDetailResponse = mapper.readValue(json)

    expectThat(response) {
      get { id }.isEqualTo("07MALFORM")
      get { execution?.stages }.isNotNull().hasSize(0)
    }
  }

  @Test
  fun `ExecutionDetailResponse requires id field`() {
    val json = """
      {
        "name": "test-execution",
        "application": "myapp",
        "buildTime": 1609459200000,
        "status": "SUCCEEDED"
      }
    """.trimIndent()

    try {
      mapper.readValue<ExecutionDetailResponse>(json)
      throw AssertionError("Expected IllegalArgumentException for missing id field")
    } catch (e: Exception) {
      expectThat(e.message).isNotNull()
    }
  }

  @Test
  fun `ExecutionDetailResponse requires name field`() {
    val json = """
      {
        "id": "08MISSING",
        "application": "myapp",
        "buildTime": 1609459200000,
        "status": "SUCCEEDED"
      }
    """.trimIndent()

    try {
      mapper.readValue<ExecutionDetailResponse>(json)
      throw AssertionError("Expected IllegalArgumentException for missing name field")
    } catch (e: Exception) {
      expectThat(e.message).isNotNull()
    }
  }

  @Test
  fun `ExecutionDetailResponse requires application field`() {
    val json = """
      {
        "id": "09MISSING",
        "name": "test-execution",
        "buildTime": 1609459200000,
        "status": "SUCCEEDED"
      }
    """.trimIndent()

    try {
      mapper.readValue<ExecutionDetailResponse>(json)
      throw AssertionError("Expected IllegalArgumentException for missing application field")
    } catch (e: Exception) {
      expectThat(e.message).isNotNull()
    }
  }

  @Test
  fun `ExecutionDetailResponse requires buildTime field`() {
    val json = """
      {
        "id": "10MISSING",
        "name": "test-execution",
        "application": "myapp",
        "status": "SUCCEEDED"
      }
    """.trimIndent()

    try {
      mapper.readValue<ExecutionDetailResponse>(json)
      throw AssertionError("Expected IllegalArgumentException for missing buildTime field")
    } catch (e: Exception) {
      expectThat(e.message).isNotNull()
    }
  }

  @Test
  fun `ExecutionDetailResponse requires status field`() {
    val json = """
      {
        "id": "11MISSING",
        "name": "test-execution",
        "application": "myapp",
        "buildTime": 1609459200000
      }
    """.trimIndent()

    try {
      mapper.readValue<ExecutionDetailResponse>(json)
      throw AssertionError("Expected IllegalArgumentException for missing status field")
    } catch (e: Exception) {
      expectThat(e.message).isNotNull()
    }
  }

  @Test
  fun `ExecutionDetailResponse handles null id gracefully with error`() {
    val json = """
      {
        "id": null,
        "name": "test-execution",
        "application": "myapp",
        "buildTime": 1609459200000,
        "status": "SUCCEEDED"
      }
    """.trimIndent()

    try {
      mapper.readValue<ExecutionDetailResponse>(json)
      throw AssertionError("Expected IllegalArgumentException for null id field")
    } catch (e: Exception) {
      expectThat(e.message).isNotNull()
    }
  }

  @Test
  fun `ExecutionDetailResponse with all required fields and no optional fields`() {
    val json = """
      {
        "id": "12MINIMAL",
        "name": "minimal-execution",
        "application": "myapp",
        "buildTime": 1609459200000,
        "status": "NOT_STARTED"
      }
    """.trimIndent()

    val response: ExecutionDetailResponse = mapper.readValue(json)

    expectThat(response) {
      get { id }.isEqualTo("12MINIMAL")
      get { name }.isEqualTo("minimal-execution")
      get { application }.isEqualTo("myapp")
      get { buildTime }.isEqualTo(Instant.ofEpochMilli(1609459200000))
      get { status }.isEqualTo(TaskStatus.NOT_STARTED)
      get { startTime }.isNull()
      get { endTime }.isNull()
      get { execution?.stages }.isNotNull().hasSize(0)
      get { stages }.isNotNull().hasSize(0)
      get { variables }.isNull()
    }
  }

  @Test
  fun `KeyValuePair handles complex object values`() {
    val json = """
      {
        "key": "metadata",
        "value": {
          "nested": "object",
          "count": 42
        }
      }
    """.trimIndent()

    val pair: KeyValuePair = mapper.readValue(json)

    expectThat(pair.key).isEqualTo("metadata")
    expectThat(pair.value).isA<Map<*, *>>()
  }

  @Test
  fun `KeyValuePair handles list values`() {
    val json = """
      {
        "key": "items",
        "value": ["item1", "item2", "item3"]
      }
    """.trimIndent()

    val pair: KeyValuePair = mapper.readValue(json)

    expectThat(pair.key).isEqualTo("items")
    expectThat(pair.value).isA<List<*>>()
  }

  @Test
  fun `OrcaExecutionStage is properly typed as Map`() {
    val stage: OrcaExecutionStage = mapOf(
      "id" to "stage-1",
      "type" to "wait",
      "name" to "Wait Stage",
      "status" to "SUCCEEDED"
    )

    expectThat(stage).isA<Map<String, Any>>()
    expectThat(stage["id"]).isEqualTo("stage-1")
    expectThat(stage["type"]).isEqualTo("wait")
  }
}
