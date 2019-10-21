/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.api.titus.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ClusterDependencies
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.api.titus.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.titus.SPINNAKER_TITUS_API_V1
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.TitusActiveServerGroup
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.context.ApplicationEventPublisher
import retrofit2.HttpException
import java.time.Clock

class TitusClusterHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val clock: Clock,
  private val publisher: ApplicationEventPublisher,
  objectMapper: ObjectMapper,
  resolvers: List<Resolver<*>>
) : ResourceHandler<TitusClusterSpec, Map<String, TitusServerGroup>>(objectMapper, resolvers) {

  override val apiVersion = SPINNAKER_TITUS_API_V1
  override val supportedKind = ResourceKind(
    group = apiVersion.group,
    singular = "titus-cluster",
    plural = "titus-clusters"
  ) to TitusClusterSpec::class.java

  override suspend fun toResolvedType(resource: Resource<TitusClusterSpec>): Map<String, TitusServerGroup> =
    with(resource.spec) {
      resolve().byRegion()
    }

  override suspend fun current(resource: Resource<TitusClusterSpec>): Map<String, TitusServerGroup>? =
    cloudDriverService
      .getServerGroups(resource)
      .byRegion()

  override suspend fun actuationInProgress(id: ResourceId) =
    orcaService
      .getCorrelatedExecutions(id.value)
      .isNotEmpty()

  private suspend fun CloudDriverService.getServerGroups(resource: Resource<TitusClusterSpec>): Iterable<TitusServerGroup> =
    coroutineScope {
      resource.spec.locations.regions.map {
        async {
          try {
            titusActiveServerGroup(
              resource.serviceAccount,
              resource.spec.moniker.app,
              resource.spec.locations.account,
              resource.spec.moniker.name,
              it.name,
              CLOUD_PROVIDER
            )
              .toTitusServerGroup()
          } catch (e: HttpException) {
            if (!e.isNotFound) {
              throw e
            }
            null
          }
        }
      }
        .mapNotNull { it.await() }
      // todo eb: how can we tell what version is deployed here?
      // todo: emit an event for the version that's deployed
    }

  private fun TitusActiveServerGroup.toTitusServerGroup() =
    TitusServerGroup(
      name = name,
      location = Location(
        account = placement.account,
        region = region
      ),
      capacity = capacity,
      container = Container(
        image = image.dockerImageName,
        digest = image.dockerImageDigest,
        tag = ""
      ),
      containerOptions = ContainerOptions(
        entryPoint = entryPoint,
        resources = resources,
        env = env,
        constraints = constraints,
        iamProfile = iamProfile,
        capacityGroup = capacityGroup,
        migrationPolicy = migrationPolicy
      ),
      dependencies = ClusterDependencies(
        loadBalancers,
        securityGroupNames = securityGroupNames,
        targetGroups = targetGroups
      )
    )

  private val TitusServerGroup.securityGroupIds: Collection<String>
    get() = dependencies
      .securityGroupNames
      // no need to specify these as Orca will auto-assign them, also the application security group
      // gets auto-created so may not exist yet
      .filter { it !in setOf("nf-infrastructure", "nf-datacenter", moniker.app) }
      .map {
        cloudDriverCache.securityGroupByName(location.account, location.region, it).id
      }

  private val TitusActiveServerGroup.securityGroupNames: Set<String>
    get() = securityGroups.map {
      cloudDriverCache.securityGroupById(awsAccount, region, it).name
    }
      .toSet()
}
