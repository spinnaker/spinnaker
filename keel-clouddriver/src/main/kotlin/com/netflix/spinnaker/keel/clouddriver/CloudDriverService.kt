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
import com.netflix.spinnaker.keel.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import kotlinx.coroutines.Deferred
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudDriverService {

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  fun getSecurityGroup(
    @Path("account") account: String,
    @Path("type") type: String,
    @Path("securityGroupName") securityGroupName: String,
    @Path("region") region: String,
    @Query("vpcId") vpcId: String? = null
  ): Deferred<SecurityGroup>

  @GET("/securityGroups/{account}/{provider}")
  fun getSecurityGroupSummaries(
    @Path("account") account: String,
    @Path("provider") provider: String,
    @Query("region") region: String
  ): Deferred<Collection<SecurityGroupSummary>>

  @GET("/networks")
  fun listNetworks(): Deferred<Map<String, Set<Network>>>

  @GET("/networks/{cloudProvider}")
  fun listNetworksByCloudProvider(@Path("cloudProvider") cloudProvider: String): Deferred<Set<Network>>

  @GET("/subnets/{cloudProvider}")
  fun listSubnets(@Path("cloudProvider") cloudProvider: String): Deferred<Set<Subnet>>

  @GET("/credentials")
  fun listCredentials(): Deferred<Set<Credential>>

  @GET("/credentials/{account}")
  fun getCredential(@Path("account") account: String): Deferred<Credential>

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  fun getLoadBalancer(
    @Path("provider") provider: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): Deferred<List<LoadBalancer>>

  @GET("/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{region}/serverGroups/target/current_asg_dynamic?onlyEnabled=true")
  fun activeServerGroup(
    @Path("app") app: String,
    @Path("account") account: String,
    @Path("cluster") cluster: String,
    @Path("region") region: String,
    @Path("cloudProvider") cloudProvider: String
  ): Deferred<ClusterActiveServerGroup>

  @GET("/aws/images/find")
  fun namedImages(
    @Query("q") imageName: String,
    @Query("account") account: String?,
    @Query("region") region: String? = null
  ): Deferred<List<NamedImage>>
}
