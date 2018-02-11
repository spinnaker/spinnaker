package com.netflix.spinnaker.keel.clouddriver

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import java.util.concurrent.TimeUnit

class MemoryCloudDriverCache(
  private val cloudDriver: CloudDriverService
) : CloudDriverCache {

  private val securityGroupSummaries = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build<String, SecurityGroupSummary>()

  private val networks = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build<String, Network>()

  private val availabilityZones = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build<String, Set<String>>()

  private val credentials = CacheBuilder.newBuilder()
    .maximumSize(100)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build<String, Credential>()

  private fun credentialBy(name: String): Credential =
    credentials.getOrNotFound(name, "Credentials with name $name not found") {
      cloudDriver
        .getCredential(name)
    }

  override fun securityGroupSummaryBy(account: String, region: String, id: String): SecurityGroupSummary =
    securityGroupSummaries.getOrNotFound(
      "$account:$region:$id",
      "Security group with id $id not found in the $account account and $region region"
    ) {
      val credential = credentialBy(account)

      // TODO-AJ should be able to swap this out for a call to `/search`
      cloudDriver
        .getSecurityGroupSummaries(account, credential.type, region)
        .firstOrNull { it.id == id }
    }

  override fun networkBy(id: String): Network =
    networks.getOrNotFound(id, "VPC network with id $id not found") {
      cloudDriver
        .listNetworks()["aws"]
        ?.firstOrNull { it.id == id }
    }

  // TODO rz - caches here aren't very efficient
  // TODO rz - caches here aren't very efficient
  override fun networkBy(name: String?, account: String, region: String): Network =
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
      put(key, v)
    }
    return v
  }
}
