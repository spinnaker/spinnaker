package com.netflix.spinnaker.keel.grpc

import com.netflix.spinnaker.keel.model.AssetId

// TODO: need to add the plugin id
abstract class PluginRequestFailed(
  val id: AssetId,
  method: String,
  reason: String
) : RuntimeException("%s request %s failed with \"%s\"".format(method, id, reason))
