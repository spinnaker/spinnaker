package com.netflix.spinnaker.orca.remote.model

import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionPayload

/**
 * The payload sent to the remote stage on invocation.
 */
data class RemoteStageExtensionPayload(
  /** The remote stage type. */
  val type: String,

  /**  The stage execution ID. */
  val id: String,

  /** The pipeline execution ID that the stage execution ID belongs to. */
  val pipelineExecutionId: String,

  /** The stage context which will contain all the stage parameters and configuration details. */
  val context: MutableMap<String, Any?>
) : RemoteExtensionPayload
