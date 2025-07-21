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

import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.AmazonLoadBalancer
import com.netflix.spinnaker.keel.clouddriver.model.ApplicationLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.Certificate
import com.netflix.spinnaker.keel.clouddriver.model.ClassicLoadBalancerModel
import com.netflix.spinnaker.keel.clouddriver.model.Credential
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCollection
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.TitusServerGroup
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.tags.EntityTags
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudDriverService {
  @GET("securityGroups/{account}/{type}/{region}/{securityGroupName}")
  suspend fun getSecurityGroup(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("account") account: String,
    @Path("type") type: String,
    @Path("securityGroupName") securityGroupName: String,
    @Path("region") region: String,
    @Query("vpcId") vpcId: String? = null
  ): SecurityGroupModel

  @GET("securityGroups/{account}/{provider}/{region}/{id}?getById=true")
  suspend fun getSecurityGroupSummaryById(
    @Path("account") account: String,
    @Path("provider") provider: String,
    @Path("region") region: String,
    @Path("id") id: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): SecurityGroupSummary

  @GET("securityGroups/{account}/{provider}/{region}/{name}")
  suspend fun getSecurityGroupSummaryByName(
    @Path("account") account: String,
    @Path("provider") provider: String,
    @Path("region") region: String,
    @Path("name") name: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): SecurityGroupSummary

  @GET("networks/{cloudProvider}")
  suspend fun listNetworks(
    @Path("cloudProvider") cloudProvider: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Set<Network>

  @GET("subnets/{cloudProvider}")
  suspend fun listSubnets(
    @Path("cloudProvider") cloudProvider: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Set<Subnet>

  @GET("credentials/{account}")
  suspend fun getCredential(
    @Path("account") account: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Credential

  @GET("applications/{application}/loadBalancers")
  suspend fun loadBalancersForApplication(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("application") application: String
  ): List<AmazonLoadBalancer>

  @GET("aws/loadBalancers/{account}/{region}/{name}")
  suspend fun getAmazonLoadBalancer(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): List<AmazonLoadBalancer>

  @GET("{provider}/loadBalancers/{account}/{region}/{name}")
  suspend fun getClassicLoadBalancer(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("provider") provider: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): List<ClassicLoadBalancerModel>

  @GET("{provider}/loadBalancers/{account}/{region}/{name}")
  suspend fun getApplicationLoadBalancer(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("provider") provider: String,
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("name") name: String
  ): List<ApplicationLoadBalancerModel>

  @GET("applications/{app}/clusters/{account}/{cluster}/{cloudProvider}")
  suspend fun listServerGroups(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("app") app: String,
    @Path("account") account: String,
    @Path("cluster") cluster: String,
    @Path("cloudProvider") cloudProvider: String = "aws"
  ): ServerGroupCollection<ServerGroup>

  @GET("applications/{app}/clusters/{account}/{cluster}/{cloudProvider}")
  suspend fun listTitusServerGroups(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("app") app: String,
    @Path("account") account: String,
    @Path("cluster") cluster: String,
    @Path("cloudProvider") cloudProvider: String = "titus"
  ): ServerGroupCollection<TitusServerGroup>

  /**
   * Note: This endpoint does not get the latest healthy cluster, only the latest enabled cluster
   */
  @GET("applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{region}/serverGroups/target/current_asg_dynamic?onlyEnabled=true")
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
  @GET("applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{region}/serverGroups/target/current_asg_dynamic?onlyEnabled=true")
  suspend fun titusActiveServerGroup(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("app") app: String,
    @Path("account") account: String,
    @Path("cluster") cluster: String,
    @Path("region") region: String,
    @Path("cloudProvider") cloudProvider: String = "titus"
  ): TitusActiveServerGroup

  @GET("aws/images/find")
  suspend fun namedImages(
    @Header("X-SPINNAKER-USER") user: String,
    @Query("q") imageName: String,
    @Query("account") account: String?,
    @Query("region") region: String? = null
  ): List<NamedImage>

  @GET("aws/images/{account}/{region}/{id}")
  suspend fun getImage(
    @Path("account") account: String,
    @Path("region") region: String,
    @Path("id") amiId: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<NamedImage>

  @GET("dockerRegistry/images/find")
  suspend fun findDockerImages(
    @Query("account") account: String? = null,
    @Query("repository") repository: String? = null,
    @Query("tag") tag: String? = null,
    @Query("q") q: String? = null,
    @Query("includeDetails") includeDetails: Boolean? = null,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<DockerImage>

  @GET("dockerRegistry/images/tags")
  suspend fun findDockerTagsForImage(
    @Query("account") account: String,
    @Query("repository") repository: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<String>

  @GET("credentials/{account}")
  suspend fun getAccountInformation(
    @Path("account") account: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): Map<String, Any?>

  @GET("tags")
  suspend fun getEntityTags(
    @Query("cloudProvider") cloudProvider: String,
    @Query("account") account: String,
    @Query("application") application: String,
    @Query("entityType") entityType: String,
    @Query("entityId") entityId: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
  ): List<EntityTags>

  @GET("certificates/aws")
  suspend fun getCertificates() : List<Certificate>
}
