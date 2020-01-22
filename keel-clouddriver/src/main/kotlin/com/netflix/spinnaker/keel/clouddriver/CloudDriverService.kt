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

import com.netflix.spinnaker.keel.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.tags.EntityTags
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface CloudDriverService {

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  suspend fun getSecurityGroup(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("account") account: String,
    @Path("type") type: String,
    @Path("securityGroupName") securityGroupName: String,
    @Path("region") region: String,
    @Query("vpcId") vpcId: String? = null
  ): SecurityGroupModel

  @GET("/securityGroups/{account}/{provider}")
  suspend fun getSecurityGroupSummaries(
    @Path("account") account: String,
    @Path("provider") provider: String,
    @Query("region") region: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Collection<SecurityGroupSummary>

  @GET("/networks")
  suspend fun listNetworks(
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Map<String, Set<Network>>

  @GET("/networks/{cloudProvider}")
  suspend fun listNetworksByCloudProvider(
    @Path("cloudProvider") cloudProvider: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Set<Network>

  @GET("/subnets/{cloudProvider}")
  suspend fun listSubnets(
    @Path("cloudProvider") cloudProvider: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Set<Subnet>

  @GET("/credentials")
  suspend fun listCredentials(
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Set<Credential>

  @GET("/credentials/{account}")
  suspend fun getCredential(
    @Path("account") account: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Credential

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  suspend fun getClassicLoadBalancer(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("provider") provider: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): List<ClassicLoadBalancerModel>

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  suspend fun getApplicationLoadBalancer(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("provider") provider: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): List<ApplicationLoadBalancerModel>

  @GET("/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{region}/serverGroups/target/current_asg_dynamic?onlyEnabled=true")
  suspend fun activeServerGroup(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("app") app: String,
    @Path("account") account: String,
    @Path("cluster") cluster: String,
    @Path("region") region: String,
    @Path("cloudProvider") cloudProvider: String
  ): ActiveServerGroup

  // todo eb: titus has different fields than [ActiveServerGroup], so right now this is a separate call
  // make above call general and roll titus into it.
  @GET("/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{region}/serverGroups/target/current_asg_dynamic?onlyEnabled=true")
  suspend fun titusActiveServerGroup(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("app") app: String,
    @Path("account") account: String,
    @Path("cluster") cluster: String,
    @Path("region") region: String,
    @Path("cloudProvider") cloudProvider: String = "titus"
  ): TitusActiveServerGroup

  @GET("/aws/images/find")
  suspend fun namedImages(
    @Header("X-SPINNAKER-USER") user: String,
    @Query("q") imageName: String,
    @Query("account") account: String?,
    @Query("region") region: String? = null
  ): List<NamedImage>

  @GET("/images/find")
  suspend fun images(
    @Query("provider") provider: String,
    @Query("q") name: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<NamedImage>

  @GET("/dockerRegistry/images/find")
  suspend fun findDockerImages(
    @Query("account") account: String? = null,
    @Query("repository") repository: String? = null,
    @Query("tag") tag: String? = null,
    @Query("q") q: String? = null,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<DockerImage>

  @GET("/dockerRegistry/images/tags")
  suspend fun findDockerTagsForImage(
    @Query("account") account: String,
    @Query("repository") repository: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<String>

  @GET("/tags/{entityId}")
  suspend fun getTagsForEntity(
    @Path("entityId") entityId: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): EntityTags

  @GET("/tags")
  suspend fun getTagsForParams(
    @QueryMap allParameters: Map<String, String>,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): EntityTags

  @GET("/credentials/{account}")
  suspend fun getAccountInformation(
    @Path("account") account: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Map<String, Any?>
}
