package com.netflix.spinnaker.keel.api

import java.time.Duration

data class PipelineConstraint(
  val timeout: Duration = Duration.ofHours(2),
  val pipelineId: String,
  val retries: Int = 0,
  val parameters: Map<String, Any?> = emptyMap()
) : StatefulConstraint("pipeline")
