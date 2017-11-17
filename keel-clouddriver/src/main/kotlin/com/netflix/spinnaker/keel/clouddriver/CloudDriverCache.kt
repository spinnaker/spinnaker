package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup

interface CloudDriverCache {
  fun securityGroupBy(account: String, id: String): SecurityGroup
  fun networkBy(id: String): Network
  fun networkBy(name: String, account: String, region: String): Network
  fun availabilityZonesBy(account: String, vpcId: String, region: String): Set<String>
}

class ResourceNotFound(message: String) : RuntimeException(message)
