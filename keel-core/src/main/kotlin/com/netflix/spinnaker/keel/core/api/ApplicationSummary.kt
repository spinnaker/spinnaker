package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = ["deliveryConfigName", "application", "serviceAccount", "apiVersion", "isPaused"])
data class ApplicationSummary(
  val deliveryConfigName: String,
  val application: String,
  val serviceAccount: String,
  val apiVersion: String,
  val isPaused: Boolean = false
)
