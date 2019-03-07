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

import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudDriverService {

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  suspend fun getSecurityGroup(
    @Path("account") account: String,
    @Path("type") type: String,
    @Path("securityGroupName") securityGroupName: String,
    @Path("region") region: String,
    @Query("vpcId") vpcId: String? = null
  ): SecurityGroup

  @GET("/securityGroups/{account}/{provider}")
  suspend fun getSecurityGroupSummaries(
    @Path("account") account: String,
    @Path("provider") provider: String,
    @Query("region") region: String
  ): Collection<SecurityGroupSummary>

  @GET("/networks")
  suspend fun listNetworks(): Map<String, Set<Network>>

  @GET("/networks/{cloudProvider}")
  suspend fun listNetworksByCloudProvider(@Path("cloudProvider") cloudProvider: String): Set<Network>

  @GET("/subnets/{cloudProvider}")
  suspend fun listSubnets(@Path("cloudProvider") cloudProvider: String): Set<Subnet>

  @GET("/credentials")
  suspend fun listCredentials(): Set<Credential>

  @GET("/credentials/{account}")
  suspend fun getCredential(@Path("account") account: String): Credential

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  suspend fun getClassicLoadBalancer(
    @Path("provider") provider: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): List<ClassicLoadBalancerModel>

  @GET("/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{region}/serverGroups/target/current_asg_dynamic?onlyEnabled=true")
  suspend fun activeServerGroup(
    @Path("app") app: String,
    @Path("account") account: String,
    @Path("cluster") cluster: String,
    @Path("region") region: String,
    @Path("cloudProvider") cloudProvider: String
  ): ClusterActiveServerGroup

  @GET("/aws/images/find")
  suspend fun namedImages(
    @Query("q") imageName: String,
    @Query("account") account: String?,
    @Query("region") region: String? = null
  ): List<NamedImage>

  @GET("/images/find")
  suspend fun images(
    @Query("provider") provider: String,
    @Query("q") name: String
  ): List<NamedImage>
}
