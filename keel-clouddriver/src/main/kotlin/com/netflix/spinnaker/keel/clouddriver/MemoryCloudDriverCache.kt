package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import org.springframework.stereotype.Component

@Component
class MemoryCloudDriverCache(
  private val cloudDriver: ClouddriverService
) : CloudDriverCache {
  override fun securityGroupBy(account: String, id: String): SecurityGroup =
    cloudDriver
      .getSecurityGroups(account)
      .firstOrNull { it.id == id }
      ?: throw ResourceNotFound("Security group with id $id not found in the $account account")

  override fun networkBy(id: String): Network =
    cloudDriver
      .listNetworks()["aws"]
      ?.firstOrNull { it.id == id }
      ?: throw ResourceNotFound("VPC network with id $id not found")

  override fun networkBy(name: String, account: String, region: String): Network =
    cloudDriver
      .listNetworks()["aws"]
      ?.firstOrNull { it.name == name && it.account == account && it.region == region }
      ?: throw ResourceNotFound("VPC network named $name not found in $region")

  override fun availabilityZonesBy(account: String, vpcId: String, region: String): Set<String> =
    cloudDriver
      .listSubnets("aws")
      .filter { it.account == account && it.vpcId == vpcId && it.region == region }
      .map { it.availabilityZone }
      .toSet()
}
