package com.netflix.spinnaker.keel.api.artifacts

import com.netflix.spinnaker.keel.api.artifacts.BaseLabel.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.StoreType.EBS

data class VirtualMachineOptions(
  val baseLabel: BaseLabel = RELEASE,
  val baseOs: String,
  val regions: Set<String>,
  val storeType: StoreType = EBS
)
