/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.clouddriver

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
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
        .getCredential(name).await()
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
        .await()
        .firstOrNull { it.id == id }
    }

  override fun networkBy(id: String): Network =
    networks.getOrNotFound(id, "VPC network with id $id not found") {
      cloudDriver
        .listNetworks()
        .await()["aws"]
        ?.firstOrNull { it.id == id }
    }

  // TODO rz - caches here aren't very efficient
  // TODO rz - caches here aren't very efficient
  override fun networkBy(name: String?, account: String, region: String): Network =
    networks.getOrNotFound("$name:$account:$region", "VPC network named $name not found in $region") {
      cloudDriver
        .listNetworks()
        .await()["aws"]
        ?.firstOrNull { it.name == name && it.account == account && it.region == region }
    }

  override fun availabilityZonesBy(account: String, vpcId: String, region: String): Set<String> =
    availabilityZones.get("$account:$vpcId:$region") {
      runBlocking {
        cloudDriver
          .listSubnets("aws")
          .await()
          .filter { it.account == account && it.vpcId == vpcId && it.region == region }
          .map { it.availabilityZone }
          .toSet()
      }
    }

  private fun <T> Cache<String, T>.getOrNotFound(
    key: String,
    notFoundMessage: String,
    loader: suspend CoroutineScope.() -> T?
  ): T {
    var v = getIfPresent(key)
    if (v == null) {
      v = runBlocking(block = loader)
      if (v == null) {
        throw ResourceNotFound(notFoundMessage)
      }
      put(key, v)
    }
    return v
  }
}
