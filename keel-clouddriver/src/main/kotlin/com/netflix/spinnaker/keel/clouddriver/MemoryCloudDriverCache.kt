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

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException

class MemoryCloudDriverCache(
  private val cloudDriver: CloudDriverService
) : CloudDriverCache {

  private val securityGroupSummariesByIdOrName = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, MINUTES)
    .build<String, SecurityGroupSummary>()

  private val networks = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, HOURS)
    .build<String, Network>()

  private val availabilityZones = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, HOURS)
    .build<String, Set<String>>()

  private val credentials = Caffeine.newBuilder()
    .maximumSize(100)
    .expireAfterWrite(1, HOURS)
    .build<String, Credential>()

  private val subnetsById = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, HOURS)
    .build<String, Subnet>()

  private val subnetsByPurpose = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, HOURS)
    .build<String, Subnet>()

  override fun credentialBy(name: String): Credential =
    credentials.getOrNotFound(name, "Credentials with name $name not found") {
      cloudDriver.getCredential(name, DEFAULT_SERVICE_ACCOUNT)
    }

  override fun securityGroupById(account: String, region: String, id: String): SecurityGroupSummary =
    securityGroupSummariesByIdOrName.getOrNotFound(
      "$account:$region:$id",
      "Security group with id $id not found in the $account account and $region region"
    ) {
      val credential = credentialBy(account)
      cloudDriver.getSecurityGroupSummaryById(account, credential.type, region, id, DEFAULT_SERVICE_ACCOUNT)
    }.also {
      securityGroupSummariesByIdOrName.put("$account:$region:${it.name}", it)
    }

  override fun securityGroupByName(account: String, region: String, name: String): SecurityGroupSummary =
    securityGroupSummariesByIdOrName.getOrNotFound(
      "$account:$region:$name",
      "Security group with name $name not found in the $account account and $region region"
    ) {
      val credential = credentialBy(account)
      cloudDriver.getSecurityGroupSummaryByName(account, credential.type, region, name, DEFAULT_SERVICE_ACCOUNT)
    }.also {
      securityGroupSummariesByIdOrName.put("$account:$region:${it.id}", it)
    }

  override fun networkBy(id: String): Network =
    networks.getOrNotFound(id, "VPC network with id $id not found") {
      cloudDriver
        .listNetworks(DEFAULT_SERVICE_ACCOUNT)["aws"]
        ?.firstOrNull { it.id == id }
    }

  // TODO rz - caches here aren't very efficient
  override fun networkBy(name: String?, account: String, region: String): Network =
    networks.getOrNotFound("$name:$account:$region", "VPC network named $name not found in $region") {
      cloudDriver
        .listNetworks(DEFAULT_SERVICE_ACCOUNT)["aws"]
        ?.firstOrNull { it.name == name && it.account == account && it.region == region }
    }

  override fun availabilityZonesBy(account: String, vpcId: String, purpose: String, region: String): Set<String> =
    availabilityZones.get("$account:$vpcId:$purpose:$region") {
      runBlocking {
        cloudDriver
          .listSubnets("aws", DEFAULT_SERVICE_ACCOUNT)
          .filter { it.account == account && it.vpcId == vpcId && it.purpose == purpose && it.region == region }
          .map { it.availabilityZone }
          .toSet()
      }
    }!!

  override fun subnetBy(subnetId: String): Subnet =
    subnetsById.getOrNotFound(subnetId, "Subnet with id $subnetId not found") {
      cloudDriver
        .listSubnets("aws", DEFAULT_SERVICE_ACCOUNT)
        .find { it.id == subnetId }
    }

  override fun subnetBy(account: String, region: String, purpose: String): Subnet =
    subnetsByPurpose.getOrNotFound("$account:$region:$purpose", "Subnet with purpose \"$purpose\" not found in $account:$region") {
      cloudDriver
        .listSubnets("aws", DEFAULT_SERVICE_ACCOUNT)
        .find { it.account == account && it.region == region && it.purpose == purpose }
    }

  private fun <T> Cache<String, T>.getOrNotFound(
    key: String,
    notFoundMessage: String,
    loader: suspend CoroutineScope.() -> T?
  ): T = get(key) {
    runCatching {
      runBlocking(block = loader)
    }
      .getOrElse { ex ->
        if (ex is HttpException && ex.code() == 404) {
          null
        } else {
          throw CacheLoadingException("Error loading cache for $key", ex)
        }
      }
  } ?: throw ResourceNotFound(notFoundMessage)
}
