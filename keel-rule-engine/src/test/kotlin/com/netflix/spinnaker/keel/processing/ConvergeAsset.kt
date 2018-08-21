package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.q.Message

data class ConvergeAsset(val id: AssetId) : Message()
