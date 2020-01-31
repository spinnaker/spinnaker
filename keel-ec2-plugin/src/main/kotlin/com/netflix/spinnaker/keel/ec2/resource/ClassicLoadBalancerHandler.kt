package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerListener
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerOverride
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerDependencies
import com.netflix.spinnaker.keel.api.ec2.Location
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ResourceNotFound
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.diff.toIndividualDiffs
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.SupportedKind
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import com.netflix.spinnaker.keel.retrofit.isNotFound
import java.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

class ClassicLoadBalancerHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val taskLauncher: TaskLauncher,
  resolvers: List<Resolver<*>>
) : ResourceHandler<ClassicLoadBalancerSpec, Map<String, ClassicLoadBalancer>>(resolvers) {

  override val supportedKind =
    SupportedKind(SPINNAKER_EC2_API_V1, "classic-load-balancer", ClassicLoadBalancerSpec::class.java)

  override suspend fun toResolvedType(resource: Resource<ClassicLoadBalancerSpec>): Map<String, ClassicLoadBalancer> =
    with(resource.spec) {
      locations.regions.map { region ->
        ClassicLoadBalancer(
          moniker,
          Location(
            account = locations.account,
            region = region.name,
            vpc = locations.vpc ?: error("No vpc supplied or resolved"),
            subnet = locations.subnet ?: error("No subnet purpose supplied or resolved"),
            availabilityZones = region.availabilityZones
          ),
          internal,
          overrides[region.name]?.dependencies ?: dependencies,
          overrides[region.name]?.listeners ?: listeners,
          overrides[region.name]?.healthCheck ?: healthCheck,
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
          val description = "$action ${resource.kind} load balancer ${desired.moniker} in ${desired.location.account}/${desired.location.region}"

          async {
            taskLauncher.submitJob(
              resource = resource,
              description = description,
              correlationId = "${resource.id}:${desired.location.region}",
              job = diff.toUpsertJob()
            )
          }
        }
        .map { it.await() }
    }

  override suspend fun export(exportable: Exportable): ClassicLoadBalancerSpec {
    val clbs = cloudDriverService.getClassicLoadBalancer(
      account = exportable.account,
      name = exportable.moniker.toString(),
      regions = exportable.regions,
      serviceAccount = exportable.user)

    if (clbs.isEmpty()) {
      throw ResourceNotFound("Could not find classic load balancer: ${exportable.moniker} " +
        "in account: ${exportable.account} for ")
    }
    val zonesByRegion = clbs.map { (region, clb) ->
      region to cloudDriverCache.availabilityZonesBy(
        account = exportable.account,
        vpcId = cloudDriverCache.subnetBy(exportable.account, region, clb.location.subnet).vpcId,
        purpose = clb.location.subnet,
        region = region
      )
    }
      .toMap()

    val zonesForCLB = clbs.map { (region, clb) ->
      region to if (
        clb.location.availabilityZones
          .containsAll(zonesByRegion[region]
            ?: error(
              "Failed resolving availabilityZones for account: ${exportable.account}, region: $region, " +
                "subnet: ${clb.location.subnet}")
          )
      ) {
        emptySet()
      } else {
        clb.location.availabilityZones
      }
    }.toMap()

    val base = clbs.values.first()
    val spec = ClassicLoadBalancerSpec(
      moniker = base.moniker,
      locations = SubnetAwareLocations(
        account = exportable.account,
        vpc = base.location.vpc,
        subnet = base.location.subnet,
        regions = clbs.map { (region, _) ->
          SubnetAwareRegionSpec(
            name = region,
            availabilityZones = zonesForCLB.getValue(region)
          )
        }.toSet()
      ),
      internal = base.internal,
      dependencies = base.dependencies,
      idleTimeout = base.idleTimeout,
      listeners = base.listeners,
      healthCheck = base.healthCheck,
      overrides = mutableMapOf()
    )

    spec.generateOverrides(clbs)

    return spec
  }

  override suspend fun actuationInProgress(resource: Resource<ClassicLoadBalancerSpec>): Boolean =
    resource
      .spec
      .locations
      .regions
      .map { it.name }
      .any { region ->
        orcaService
          .getCorrelatedExecutions("${resource.id}:$region")
          .isNotEmpty()
      }

  private suspend fun CloudDriverService.getClassicLoadBalancer(
    spec: ClassicLoadBalancerSpec,
    serviceAccount: String
  ) = getClassicLoadBalancer(
    account = spec.locations.account,
    name = spec.moniker.toString(),
    regions = spec.locations.regions.map { it.name }.toSet(),
    serviceAccount = serviceAccount
  )

  private suspend fun CloudDriverService.getClassicLoadBalancer(
    account: String,
    name: String,
    regions: Set<String>,
    serviceAccount: String
  ): Map<String, ClassicLoadBalancer> =
    regions.map { region ->
      coroutineScope {
        async {
          try {
            getClassicLoadBalancer(
              serviceAccount,
              CLOUD_PROVIDER,
              account,
              region,
              name
            )
              .firstOrNull()
              ?.let { lb ->
                val securityGroupNames = lb.securityGroups.map {
                  cloudDriverCache.securityGroupById(account, region, it).name
                }.toMutableSet()

                /***
                 * Clouddriver creates an "$application-elb" security group if one doesn't already exist and
                 * attaches it when creating a new AmazonLoadBalancer, see IngressLoadBalancerBuilder. If not
                 * specified in the resource spec, we should remove it from current prior to diffing.
                 */
                ClassicLoadBalancer(
                  moniker = if (lb.moniker != null) {
                    Moniker(lb.moniker!!.app, lb.moniker!!.stack, lb.moniker!!.detail)
                  } else {
                    parseMoniker(lb.loadBalancerName)
                  },
                  location = Location(
                    account = account,
                    region = region,
                    vpc = lb.vpcId.let { cloudDriverCache.networkBy(it).name }
                      ?: error("Keel does not support load balancers that are not in a VPC subnet"),
                    subnet = cloudDriverCache.subnetBy(lb.subnets.first()).purpose
                      ?: error("Keel does not support load balancers that are not in a VPC subnet"),
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

  private fun ClassicLoadBalancerSpec.generateOverrides(
    regionalClbs: Map<String, ClassicLoadBalancer>
  ) =
    regionalClbs.forEach { (region, clb) ->
      val dependenciesDiff = DefaultResourceDiff(clb.dependencies, dependencies).hasChanges()
      val listenersDiff = DefaultResourceDiff(clb.listeners, listeners).hasChanges()
      val healthCheckDiff = DefaultResourceDiff(clb.healthCheck, healthCheck).hasChanges()

      if (dependenciesDiff || listenersDiff || healthCheckDiff) {
        (overrides as MutableMap)[region] = ClassicLoadBalancerOverride(
          dependencies = if (dependenciesDiff) {
            clb.dependencies
          } else {
            null
          },
          listeners = if (listenersDiff) {
            clb.listeners
          } else {
            null
          },
          healthCheck = if (healthCheckDiff) {
            clb.healthCheck
          } else {
            null
          }
        )
      }
    }

  private fun ResourceDiff<ClassicLoadBalancer>.toUpsertJob(): Job =
    with(desired) {
      Job(
        "upsertLoadBalancer",
        mapOf(
          "application" to moniker.app,
          "credentials" to location.account,
          "cloudProvider" to CLOUD_PROVIDER,
          "name" to moniker.toString(),
          "region" to location.region,
          "availabilityZones" to mapOf(location.region to location.availabilityZones),
          "loadBalancerType" to loadBalancerType.toString().toLowerCase(),
          "vpcId" to cloudDriverCache.networkBy(location.vpc, location.account, location.region).id,
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
