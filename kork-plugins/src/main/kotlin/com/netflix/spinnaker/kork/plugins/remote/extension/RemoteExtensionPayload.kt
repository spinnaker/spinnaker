package com.netflix.spinnaker.kork.plugins.remote.extension

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.netflix.spinnaker.kork.annotations.Beta

/**
 * Marker interface for the payload for the remote extension - implemented at various extension
 * points throughout the services.
 */
@Beta
@JsonTypeInfo(
  use = JsonTypeInfo.Id.MINIMAL_CLASS,
  include = JsonTypeInfo.As.PROPERTY,
  property = "@class")
interface RemoteExtensionPayload
