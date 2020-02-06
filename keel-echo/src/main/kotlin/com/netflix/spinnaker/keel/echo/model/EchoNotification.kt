package com.netflix.spinnaker.keel.echo.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonInclude(JsonInclude.Include.NON_NULL)
data class EchoNotification(
  val notificationType: Type,
  val to: List<String>,
  val cc: List<String>? = null,
  val templateGroup: String? = null,
  val severity: Severity,
  val source: Source? = null,
  val interactiveActions: InteractiveActions?,
  val additionalContext: Map<String, Any?>? = null
) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class Source(
    val executionType: String? = null,
    val executionId: String? = null,
    val application: String? = null,
    val user: String? = null
  )

  enum class Type {
    EMAIL,
    SLACK
  }

  enum class Severity {
    NORMAL,
    HIGH
  }

  /**
   * Allows Spinnaker services sending Notifications through Echo to specify one or more interactive actions
   * that, when acted upon by a user, cause a callback to Echo which gets routed to that originating service.
   */
  data class InteractiveActions(
    val callbackServiceId: String,
    val callbackMessageId: String,
    val actions: List<InteractiveAction>,
    val color: String = "#cccccc"
  )

  @JsonTypeInfo(
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    use = JsonTypeInfo.Id.NAME,
    property = "type")
  @JsonSubTypes(
    Type(value = ButtonAction::class, name = "button")
  )
  abstract class InteractiveAction(
    val type: String,
    open val name: String,
    open val value: String
  )

  data class ButtonAction(
    override val name: String,
    override val value: String,
    val label: String? = value
  ) :
    InteractiveAction("button", name, value)

  data class InteractiveActionCallback(
    val actionPerformed: InteractiveAction,
    val serviceId: String,
    val messageId: String,
    val user: String
  )
}
