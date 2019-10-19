package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
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

class ApplicationLoadBalancerHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val environmentResolver: EnvironmentResolver,
  objectMapper: ObjectMapper,
  resolvers: List<Resolver<*>>
) : ResourceHandler<ApplicationLoadBalancerSpec, Map<String, ApplicationLoadBalancer>>(objectMapper, resolvers) {

  override val apiVersion = SPINNAKER_EC2_API_V1
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "application-load-balancer",
    "application-load-balancers"
  ) to ApplicationLoadBalancerSpec::class.java

  override suspend fun toResolvedType(resource: Resource<ApplicationLoadBalancerSpec>): Map<String, ApplicationLoadBalancer> =
    with(resource.spec) {
      locations.regions.map {
        ApplicationLoadBalancer(
          moniker,
          Location(
            account = locations.account,
            region = it.name,
            vpc = locations.vpc,
            subnet = locations.subnet ?: error("No subnet purpose supplied or resolved"),
            availabilityZones = it.availabilityZones
          ),
          internal,
          dependencies,
          idleTimeout,
          listeners,
          targetGroups
        )
      }
        .associateBy { it.location.region }
    }

  override suspend fun current(resource: Resource<ApplicationLoadBalancerSpec>): Map<String, ApplicationLoadBalancer> =
    cloudDriverService.getApplicationLoadBalancer(resource.spec, resource.serviceAccount)

  override suspend fun upsert(
    resource: Resource<ApplicationLoadBalancerSpec>,
    resourceDiff: ResourceDiff<Map<String, ApplicationLoadBalancer>>
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
            "${desired.location.account}/${desired.location.region}"

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

  private suspend fun CloudDriverService.getApplicationLoadBalancer(spec: ApplicationLoadBalancerSpec, serviceAccount: String):
    Map<String, ApplicationLoadBalancer> =
    // TODO: filtering out default rules seems wrong, see TODO in ApplicationLoadBalancerNormalizer
    spec.locations.regions.map { region ->
      coroutineScope {
        async {
          try {
            getApplicationLoadBalancer(
              serviceAccount,
              CLOUD_PROVIDER,
              spec.locations.account,
              region.name,
              spec.moniker.name
            )
              .firstOrNull()
              ?.let { lb ->
                val securityGroupNames = lb.securityGroups.map {
                  cloudDriverCache.securityGroupById(spec.locations.account, region.name, it).name
                }.toMutableSet()

                ApplicationLoadBalancer(
                  moniker = if (lb.moniker != null) {
                    Moniker(lb.moniker!!.app, lb.moniker!!.stack, lb.moniker!!.detail)
                  } else {
                    val parsedNamed = lb.loadBalancerName.split("-")
                    Moniker(app = parsedNamed[0], stack = parsedNamed.getOrNull(1), detail = parsedNamed.getOrNull(2))
                  },
                  location = Location(
                    account = spec.locations.account,
                    region = region.name,
                    vpc = lb.vpcId.let { cloudDriverCache.networkBy(it).name }
                      ?: error("Keel does not support load balancers that are not in a VPC subnet"),
                    subnet = cloudDriverCache.subnetBy(lb.subnets.first()).purpose
                      ?: error("Keel does not support load balancers that are not in a VPC subnet"),
                    availabilityZones = lb.availabilityZones
                  ),
                  internal = lb.scheme != null && lb.scheme!!.contains("internal", ignoreCase = true),
                  listeners = lb.listeners.map { l ->
                    ApplicationLoadBalancerSpec.Listener(
                      port = l.port,
                      protocol = l.protocol,
                      certificateArn = l.certificates?.firstOrNull()?.certificateArn,
                      // TODO: filtering out default rules seems wrong, see TODO in ApplicationLoadBalancerNormalizer
                      rules = l.rules.filter { !it.default }.toSet(),
                      defaultActions = l.defaultActions.toSet()
                    )
                  }.toSet(),
                  dependencies = LoadBalancerDependencies(
                    securityGroupNames = securityGroupNames
                  ),
                  targetGroups = lb.targetGroups.map { tg ->
                    ApplicationLoadBalancerSpec.TargetGroup(
                      name = tg.targetGroupName,
                      targetType = tg.targetType,
                      protocol = tg.protocol,
                      port = tg.port,
                      healthCheckEnabled = tg.healthCheckEnabled,
                      healthCheckTimeoutSeconds = Duration.ofSeconds(tg.healthCheckTimeoutSeconds.toLong()),
                      healthCheckPort = tg.healthCheckPort,
                      healthCheckProtocol = tg.healthCheckProtocol,
                      healthCheckHttpCode = tg.matcher.httpCode,
                      healthCheckPath = tg.healthCheckPath,
                      healthCheckIntervalSeconds = Duration.ofSeconds(tg.healthCheckIntervalSeconds.toLong()),
                      healthyThresholdCount = tg.healthyThresholdCount,
                      unhealthyThresholdCount = tg.unhealthyThresholdCount,
                      attributes = tg.attributes
                    )
                  }.toSet()
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

  private fun ResourceDiff<Map<String, ApplicationLoadBalancer>>.toIndividualDiffs() =
    desired
      .map { (region, desired) ->
        ResourceDiff(desired, current?.get(region))
      }

  private fun ResourceDiff<ApplicationLoadBalancer>.toUpsertJob(): Job =
    with(desired) {
      Job(
        "upsertLoadBalancer",
        mapOf(
          "application" to moniker.app,
          "credentials" to location.account,
          "cloudProvider" to CLOUD_PROVIDER,
          "name" to moniker.name,
          "region" to location.region,
          "availabilityZones" to mapOf(location.region to location.availabilityZones),
          "loadBalancerType" to loadBalancerType.toString().toLowerCase(),
          "vpcId" to cloudDriverCache.networkBy(location.vpc, location.account, location.region).id,
          "subnetType" to location.subnet,
          "isInternal" to internal,
          "idleTimeout" to idleTimeout.seconds,
          "securityGroups" to dependencies.securityGroupNames,
          "listeners" to listeners,
          "targetGroups" to targetGroups.map {
            mapOf(
              "name" to it.name,
              "targetType" to it.targetType,
              "protocol" to it.protocol,
              "port" to it.port,
              "healthCheckEnabled" to it.healthCheckEnabled,
              "healthCheckTimeoutSeconds" to it.healthCheckTimeoutSeconds.seconds,
              "healthCheckPort" to it.healthCheckPort,
              "healthCheckProtocol" to it.healthCheckProtocol,
              "healthCheckHttpCode" to it.healthCheckHttpCode,
              "healthCheckPath" to it.healthCheckPath,
              "healthCheckIntervalSeconds" to it.healthCheckIntervalSeconds.seconds,
              "healthyThresholdCount" to it.healthyThresholdCount,
              "unhealthyThresholdCount" to it.unhealthyThresholdCount,
              "attributes" to it.attributes
            )
          }
        )
      )
    }
}
