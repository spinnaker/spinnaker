package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.TaskExecution
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.serialization.mapper
import java.time.Instant

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
class ExecutionDetailResponseDeserializer : StdNodeBasedDeserializer<ExecutionDetailResponse>(ExecutionDetailResponse::class.java) {
  override fun convert(root: JsonNode, ctxt: DeserializationContext) =
    ExecutionDetailResponse(
      id = root.path("id").textValue(),
      name = root.path("name").textValue(),
      application = root.path("application").textValue(),
      buildTime = Instant.ofEpochMilli(root.path("buildTime").longValue()),
      startTime = root.path("startTime")?.longValue()?.let { Instant.ofEpochMilli(it) },
      endTime = root.path("endTime")?.longValue()?.let { Instant.ofEpochMilli(it) },
      status = ctxt.mapper.convertValue(root.path("status")),
      execution = root.path("execution")?.let { ctxt.mapper.convertValue<OrcaExecutionStages>(it) },
      stages =  root.path("stages")?.let { ctxt.mapper.convertValue<List<OrcaExecutionStage>>(it) },
      variables = root.path("variables")?.let { ctxt.mapper.convertValue<List<KeyValuePair>>(it) }
    )
}
