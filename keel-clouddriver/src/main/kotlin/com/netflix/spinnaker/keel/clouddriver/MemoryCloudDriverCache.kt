package com.netflix.spinnaker.keel.clouddriver

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import java.util.concurrent.TimeUnit

class MemoryCloudDriverCache(
  private val cloudDriver: CloudDriverService
) : CloudDriverCache {

  private val securityGroups = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build<String, SecurityGroup>()

  private val networks = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build<String, Network>()

  private val availabilityZones = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build<String, Set<String>>()

  override fun securityGroupBy(account: String, id: String): SecurityGroup =
    securityGroups.getOrNotFound("$account:$id", "Security group with id $id not found in the $account account") {
      cloudDriver
        .getSecurityGroups(account)
        .firstOrNull { it.id == id }
    }

  override fun networkBy(id: String): Network =
    networks.getOrNotFound(id, "VPC network with id $id not found") {
      cloudDriver
        .listNetworks()["aws"]
        ?.firstOrNull { it.id == id }
    }

  // TODO rz - caches here aren't very efficient
  override fun networkBy(name: String, account: String, region: String): Network =
    networks.getOrNotFound("$name:$account:$region", "VPC network named $name not found in $region") {
      cloudDriver
        .listNetworks()["aws"]
        ?.firstOrNull { it.name == name && it.account == account && it.region == region }
    }

  override fun availabilityZonesBy(account: String, vpcId: String, region: String): Set<String> =
    availabilityZones.get("$account:$vpcId:$region") {
      cloudDriver
        .listSubnets("aws")
        .filter { it.account == account && it.vpcId == vpcId && it.region == region }
        .map { it.availabilityZone }
        .toSet()
    }

  private fun <T> Cache<String, T>.getOrNotFound(key: String, notFoundMessage: String, loader: () -> T?): T {
    var v = getIfPresent(key)
    if (v == null) {
      v = loader.invoke()
      if (v == null) {
        throw ResourceNotFound(notFoundMessage)
      }
      put(key, loader.invoke())
    }
    return v
  }
}
