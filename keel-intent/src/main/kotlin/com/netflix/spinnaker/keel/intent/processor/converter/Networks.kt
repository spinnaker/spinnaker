package com.netflix.spinnaker.keel.intent.processor.converter

import com.netflix.spinnaker.keel.clouddriver.model.Network

internal fun networkIdToName(
  networks: Map<String, Set<Network>>,
  cloudProvider: String,
  region: String,
  networkId: String?
) =
  if (networkId == null) {
    null
  } else {
    networks[cloudProvider]?.first {
      it.id == networkId && it.region == region
    }?.name
  }

internal fun networkNameToId(
  networks: Map<String, Set<Network>>,
  cloudProvider: String,
  region: String,
  networkName: String?
) =
  if (networkName == null) {
    null
  } else {
    networks[cloudProvider]?.first {
      it.name == networkName && it.region == region
    }?.id
  }
