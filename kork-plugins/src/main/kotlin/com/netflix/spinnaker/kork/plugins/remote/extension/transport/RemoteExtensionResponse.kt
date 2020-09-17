package com.netflix.spinnaker.kork.plugins.remote.extension.transport

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.kork.annotations.Beta

/**
 * Response for synchronous process of writing or reading from a remote extension.
 */
@Beta
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type"
)
interface RemoteExtensionResponse

/**
 * Default no-op response such that implementing [RemoteExtensionTransport.read] and [RemoteExtensionTransport.write]
 * are optional.
 */
@JsonTypeName("noOpResponse")
class NoOpRemoteExtensionResponse : RemoteExtensionResponse
