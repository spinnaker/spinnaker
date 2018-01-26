package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary

interface CloudDriverCache {
  fun securityGroupSummaryBy(account: String, region: String, id: String): SecurityGroupSummary
  fun networkBy(id: String): Network
  fun networkBy(name: String?, account: String, region: String): Network
  fun availabilityZonesBy(account: String, vpcId: String, region: String): Collection<String>
}

class ResourceNotFound(message: String) : RuntimeException(message)
