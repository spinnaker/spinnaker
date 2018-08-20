package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.q.Message

internal data class ValidateAsset(
  val id: AssetId
) : Message()
