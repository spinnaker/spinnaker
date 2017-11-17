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

interface ClouddriverService {

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  fun getSecurityGroup(@Path("account") account: String,
                       @Path("type") type: String,
                       @Path("securityGroupName") securityGroupName: String,
                       @Path("region") region: String): SecurityGroup?

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  fun getSecurityGroup(@Path("account") account: String,
                       @Path("type") type: String,
                       @Path("securityGroupName") securityGroupName: String,
                       @Path("region") region: String,
                       @Query("vpcId") vpcId: String): SecurityGroup?

  @GET("/securityGroups/{account}")
  fun getSecurityGroups(@Path("account") account: String): Collection<SecurityGroup>

  @GET("/networks")
  fun listNetworks(): Map<String, Set<Network>>

  @GET("/networks/{cloudProvider}")
  fun listNetworksByCloudProvider(@Path("cloudProvider") cloudProvider: String): Set<Network>

  @GET("/subnets/{cloudProvider}")
  fun listSubnets(@Path("cloudProvider") cloudProvider: String): Set<Subnet>

  @GET("/credentials")
  fun listCredentials(): Set<Credential>

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  fun getElasticLoadBalancer(
    @Path("provider") provider: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): Set<ElasticLoadBalancer>
}
