package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerListener
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerDependencies
import com.netflix.spinnaker.keel.api.ec2.Location
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import java.time.Duration

class ClassicLoadBalancerHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val environmentResolver: EnvironmentResolver,
  objectMapper: ObjectMapper,
  resolvers: List<Resolver<*>>
) : ResourceHandler<ClassicLoadBalancerSpec, Map<String, ClassicLoadBalancer>>(objectMapper, resolvers) {

  override val apiVersion = SPINNAKER_EC2_API_V1
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "classic-load-balancer",
    "classic-load-balancers"
  ) to ClassicLoadBalancerSpec::class.java

  override suspend fun toResolvedType(resource: Resource<ClassicLoadBalancerSpec>): Map<String, ClassicLoadBalancer> =
    with(resource.spec) {
      locations.regions.map {
        ClassicLoadBalancer(
          moniker,
          Location(locations.accountName, it.region, locations.vpcName, it.subnet, it.availabilityZones),
          internal,
          dependencies,
          listeners,
          healthCheck,
          idleTimeout
        )
      }
        .associateBy { it.location.region }
    }

  override suspend fun current(resource: Resource<ClassicLoadBalancerSpec>): Map<String, ClassicLoadBalancer> =
    cloudDriverService.getClassicLoadBalancer(resource.spec, resource.serviceAccount)

  override suspend fun upsert(
    resource: Resource<ClassicLoadBalancerSpec>,
    resourceDiff: ResourceDiff<Map<String, ClassicLoadBalancer>>
  ): List<Task> =
    coroutineScope {
      resourceDiff
        .toIndividualDiffs()
        .filter { diff -> diff.hasChanges() }
        .map { diff ->
          val desired = diff.desired
          val action = when {
            resourceDiff.current == null -> "Creating"
            else -> "Upserting"
          }

          val description = "$action ${resource.kind} load balancer ${desired.moniker.name} in " +
            "${desired.location.accountName}/${desired.location.region}"

          val notifications = environmentResolver.getNotificationsFor(resource.id)

          async {
            orcaService
              .orchestrate(
                resource.serviceAccount,
                OrchestrationRequest(
                  description,
                  desired.moniker.app,
                  description,
                  listOf(diff.toUpsertJob()),
                  OrchestrationTrigger(correlationId = resource.id.toString(), notifications = notifications)
                )
              )
              .let {
                log.info("Started task ${it.ref} to $description")
                Task(id = it.taskId, name = description)
              }
          }
        }
        .map { it.await() }
    }

  override suspend fun actuationInProgress(id: ResourceId) =
    orcaService.getCorrelatedExecutions(id.value).isNotEmpty()

  private suspend fun CloudDriverService.getClassicLoadBalancer(spec: ClassicLoadBalancerSpec, serviceAccount: String): Map<String, ClassicLoadBalancer> =
    spec.locations.regions.map { region ->
      coroutineScope {
        async {
          try {
            getClassicLoadBalancer(
              serviceAccount,
              CLOUD_PROVIDER,
              spec.locations.accountName,
              region.region,
              spec.moniker.name
            )
              .firstOrNull()
              ?.let { lb ->
                val securityGroupNames = lb.securityGroups.map {
                  cloudDriverCache.securityGroupById(spec.locations.accountName, region.region, it).name
                }.toMutableSet()

                /***
                 * Clouddriver creates an "$application-elb" security group if one doesn't already exist and
                 * attaches it when creating a new AmazonLoadBalancer, see IngressLoadBalancerBuilder. If not
                 * specified in the resource spec, we should remove it from current prior to diffing.
                 */

                /***
                 * Clouddriver creates an "$application-elb" security group if one doesn't already exist and
                 * attaches it when creating a new AmazonLoadBalancer, see IngressLoadBalancerBuilder. If not
                 * specified in the resource spec, we should remove it from current prior to diffing.
                 */
                ClassicLoadBalancer(
                  moniker = if (lb.moniker != null) {
                    Moniker(lb.moniker!!.app, lb.moniker!!.stack, lb.moniker!!.detail)
                  } else {
                    val parsedNamed = lb.loadBalancerName.split("-")
                    Moniker(app = parsedNamed[0], stack = parsedNamed.getOrNull(1), detail = parsedNamed.getOrNull(2))
                  },
                  location = Location(
                    accountName = spec.locations.accountName,
                    region = region.region,
                    vpcName = lb.vpcId.let { cloudDriverCache.networkBy(it).name } ?: error("Keel does not support load balancers that are not in a VPC subnet"),
                    subnet = cloudDriverCache.subnetBy(lb.subnets.first()).purpose ?: error("Keel does not support load balancers that are not in a VPC subnet"),
                    availabilityZones = lb.availabilityZones
                  ),
                  internal = lb.scheme != null && lb.scheme!!.contains("internal", ignoreCase = true),
                  healthCheck = ClassicLoadBalancerHealthCheck(
                    target = lb.healthCheck.target,
                    interval = Duration.ofSeconds(lb.healthCheck.interval.toLong()),
                    healthyThreshold = lb.healthCheck.healthyThreshold,
                    unhealthyThreshold = lb.healthCheck.unhealthyThreshold,
                    timeout = Duration.ofSeconds(lb.healthCheck.timeout.toLong())
                  ),
                  idleTimeout = Duration.ofSeconds(lb.idleTimeout.toLong()),
                  listeners = lb.listenerDescriptions.map {
                    ClassicLoadBalancerListener(
                      externalProtocol = it.listener.protocol,
                      externalPort = it.listener.loadBalancerPort,
                      internalProtocol = it.listener.instanceProtocol,
                      internalPort = it.listener.instancePort,
                      sslCertificateId = it.listener.sslcertificateId
                    )
                  }.toSet(),
                  dependencies = LoadBalancerDependencies(
                    securityGroupNames = securityGroupNames
                  )
                )
              }
          } catch (e: HttpException) {
            if (e.isNotFound) {
              null
            } else {
              throw e
            }
          }
        }
      }
    }
      .mapNotNull { it.await() }
      .associateBy { it.location.region }

  private fun ResourceDiff<Map<String, ClassicLoadBalancer>>.toIndividualDiffs() =
    desired
      .map { (region, desired) ->
        ResourceDiff(desired, current?.get(region))
      }

  private fun ResourceDiff<ClassicLoadBalancer>.toUpsertJob(): Job =
    with(desired) {
      Job(
        "upsertLoadBalancer",
        mapOf(
          "application" to moniker.app,
          "credentials" to location.accountName,
          "cloudProvider" to CLOUD_PROVIDER,
          "name" to moniker.name,
          "region" to location.region,
          "availabilityZones" to mapOf(location.region to location.availabilityZones),
          "loadBalancerType" to loadBalancerType.toString().toLowerCase(),
          "vpcId" to cloudDriverCache.networkBy(location.vpcName, location.accountName, location.region).id,
          "subnetType" to location.subnet,
          "isInternal" to internal,
          "healthCheck" to healthCheck.target,
          "healthInterval" to healthCheck.interval.seconds,
          "healthyThreshold" to healthCheck.healthyThreshold,
          "unhealthyThreshold" to healthCheck.unhealthyThreshold,
          "healthTimeout" to healthCheck.timeout.seconds,
          "idleTimeout" to idleTimeout.seconds,
          "securityGroups" to dependencies.securityGroupNames,
          "listeners" to listeners
        )
      )
    }
}
