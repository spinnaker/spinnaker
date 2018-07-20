/*
 * Copyright 2017 Netflix, Inc.
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

import com.netflix.spinnaker.keel.clouddriver.model.*
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

interface CloudDriverService {

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  fun getSecurityGroup(
    @Path("account") account: String,
    @Path("type") type: String,
    @Path("securityGroupName") securityGroupName: String,
    @Path("region") region: String,
    @Query("vpcId") vpcId: String? = null
  ): SecurityGroup

  @GET("/securityGroups/{account}/{provider}")
  fun getSecurityGroupSummaries(
    @Path("account") account: String,
    @Path("provider") provider: String,
    @Query("region") region: String
  ): Collection<SecurityGroupSummary>

  @GET("/networks")
  fun listNetworks(): Map<String, Set<Network>>

  @GET("/networks/{cloudProvider}")
  fun listNetworksByCloudProvider(@Path("cloudProvider") cloudProvider: String): Set<Network>

  @GET("/subnets/{cloudProvider}")
  fun listSubnets(@Path("cloudProvider") cloudProvider: String): Set<Subnet>

  @GET("/credentials")
  fun listCredentials(): Set<Credential>

  @GET("/credentials/{account}")
  fun getCredential(@Path("account") account: String): Credential

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  fun getLoadBalancer(
    @Path("provider") provider: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): List<LoadBalancer>
}
