package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.TaskExecution
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.serialization.mapper
import java.time.Instant
import java.util.LinkedHashMap

data class TaskRefResponse(
  val ref: String
) {
  val taskId by lazy { ref.substringAfterLast("/") }
}

data class KeyValuePair(
  val key: String,
  val value: Any
)

@JsonDeserialize(using = ExecutionDetailResponseDeserializer::class)
data class ExecutionDetailResponse(
  override val id: String,
  override val name: String,
  override val application: String,
  val buildTime: Instant,
  override val startTime: Instant?,
  override val endTime: Instant?,
  override val status: TaskStatus,
  val execution: OrcaExecutionStages? = OrcaExecutionStages(emptyList()),
  val stages: List<OrcaExecutionStage>? = emptyList(), // for pipelines, stages are not encapsulated in `execution`
  val variables: List<KeyValuePair>? = null
) : TaskExecution

typealias OrcaExecutionStage = Map<String, Any>

data class OrcaStage(
  val id: String,
  val type: String,
  val name: String,
  val startTime: Instant?,
  val endTime: Instant?,
  val status: TaskStatus,
  val context: Map<String, Any>,
  val outputs: Map<String, Any>,
  val tasks: List<OrcaStageTask>,
  val syntheticStageOwner: String?, // only appears with a before stage/after stage
  val refId: String, //this is a short code for the stage, used in ordering
  val requisiteStageRefIds: List<String> //this is a coded form of what stage goes after another stage/belongs to a stage
)

data class OrcaStageTask(
  val id: String,
  val name: String,
  val implementingClass: String,
  val startTime: Instant?,
  val endTime: Instant?,
  val status: TaskStatus,
  val stageStart: Boolean,
  val stageEnd: Boolean
)

@JsonDeserialize(using = OrcaExecutionStagesDeserializer::class)
data class OrcaExecutionStages(
  val stages: List<OrcaExecutionStage>?
)

data class GeneralErrorsDetails(
  val stackTrace: String?,
  val responseBody: String?,
  val kind: String?,
  val error: String?,
  val errors: List<String>?
)

data class OrcaException(
  val exceptionType: String?,
  val shouldRetry: Boolean?,
  val details: GeneralErrorsDetails?
)

data class ClouddriverException(
  val cause: String?,
  val message: String,
  val type: String,
  val operation: String?
)

data class OrcaContext(
  // fetching only orca general and kato exceptions for now
  val exception: OrcaException?,
  @JsonAlias("kato.tasks")
  val clouddriverException: List<Map<String, Any>>?
)

/**
 * Custom deserializer for [ExecutionDetailResponse] which ensures parsing of timestamps to [Instant] is
 * done correctly since Orca's response serializes these as longs representing epoch type with milliseconds,
 * whereas our object mappers are configured to interpret them as seconds.
 */
class OrcaExecutionStagesDeserializer : StdNodeBasedDeserializer<OrcaExecutionStages>(OrcaExecutionStages::class.java) {
  private val stageListType = object : TypeReference<List<Map<String, Any>>>() {}

  override fun convert(root: JsonNode, ctxt: DeserializationContext): OrcaExecutionStages {
    val stagesNode = root.path("stages")
    val stages: List<OrcaExecutionStage>? = if (stagesNode.isMissingNode || stagesNode.isNull) {
      emptyList()
    } else {
      ctxt.mapper.readValue(ctxt.mapper.treeAsTokens(stagesNode), stageListType)
    }

    return OrcaExecutionStages(stages)
  }
}

class ExecutionDetailResponseDeserializer : StdNodeBasedDeserializer<ExecutionDetailResponse>(ExecutionDetailResponse::class.java) {
  private val stageListType = object : TypeReference<List<Map<String, Any>>>() {}
  private val keyValueListType = object : TypeReference<List<KeyValuePair>>() {}

  override fun convert(root: JsonNode, ctxt: DeserializationContext): ExecutionDetailResponse {
    // Parse execution node
    val executionNode = root.path("execution")
    val execution = if (executionNode.isMissingNode || executionNode.isNull) {
      OrcaExecutionStages(emptyList())
    } else {
      try {
        ctxt.mapper.treeToValue(executionNode, OrcaExecutionStages::class.java) ?: OrcaExecutionStages(emptyList())
      } catch (e: Exception) {
        OrcaExecutionStages(emptyList())
      }
    }

    // Parse stages node
    val stagesNode = root.path("stages")
    val stages: List<OrcaExecutionStage> = if (stagesNode.isMissingNode || stagesNode.isNull) {
      emptyList()
    } else {
      try {
        ctxt.mapper.readValue(ctxt.mapper.treeAsTokens(stagesNode), stageListType) ?: emptyList()
      } catch (e: Exception) {
        emptyList()
      }
    }

    // Parse variables node
    val variablesNode = root.path("variables")
    val variables = if (variablesNode.isMissingNode || variablesNode.isNull) {
      null
    } else {
      try {
        ctxt.mapper.readValue(ctxt.mapper.treeAsTokens(variablesNode), keyValueListType)
      } catch (e: Exception) {
        null
      }
    }

    // Parse required string fields with null safety
    val idNode = root.path("id")
    val nameNode = root.path("name")
    val applicationNode = root.path("application")
    val buildTimeNode = root.path("buildTime")
    val statusNode = root.path("status")

    require(!idNode.isMissingNode && !idNode.isNull) { "Missing required field: id" }
    require(!nameNode.isMissingNode && !nameNode.isNull) { "Missing required field: name" }
    require(!applicationNode.isMissingNode && !applicationNode.isNull) { "Missing required field: application" }
    require(!buildTimeNode.isMissingNode && !buildTimeNode.isNull) { "Missing required field: buildTime" }
    require(!statusNode.isMissingNode && !statusNode.isNull) { "Missing required field: status" }

    // Parse optional timestamp fields
    val startTimeNode = root.path("startTime")
    val endTimeNode = root.path("endTime")

    return ExecutionDetailResponse(
      id = idNode.textValue(),
      name = nameNode.textValue(),
      application = applicationNode.textValue(),
      buildTime = Instant.ofEpochMilli(buildTimeNode.longValue()),
      startTime = if (startTimeNode.isNull || startTimeNode.isMissingNode) null else Instant.ofEpochMilli(startTimeNode.longValue()),
      endTime = if (endTimeNode.isNull || endTimeNode.isMissingNode) null else Instant.ofEpochMilli(endTimeNode.longValue()),
      status = ctxt.mapper.convertValue(statusNode),
      execution = execution,
      stages = stages,
      variables = variables
    )
  }
}
