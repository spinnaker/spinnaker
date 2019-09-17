package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType
import com.netflix.spinnaker.keel.api.ec2.Location
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.retrofit.isNotFound
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.HttpException

class ApplicationLoadBalancerHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>
) : ResourceHandler<ApplicationLoadBalancer> {
  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "application-load-balancer",
    "application-load-balancers"
  ) to ApplicationLoadBalancer::class.java

  override suspend fun current(resource: Resource<ApplicationLoadBalancer>): ApplicationLoadBalancer? =
    cloudDriverService.getApplicationLoadBalancer(resource.spec, resource.serviceAccount)

  override suspend fun upsert(
    resource: Resource<ApplicationLoadBalancer>,
    resourceDiff: ResourceDiff<ApplicationLoadBalancer>
  ): List<Task> {
    val action = when {
      resourceDiff.current == null -> "Creating"
      else -> "Upserting"
    }

    val description = "$action ${resource.kind} load balancer ${resource.spec.moniker.name} in " +
      "${resource.spec.location.accountName}/${resource.spec.location.region}"

    val taskRef =
      resource.spec.let { spec ->

        orcaService
          .orchestrate(
            resource.serviceAccount,
            OrchestrationRequest(
              description,
              spec.moniker.app,
              description,
              listOf(spec.toUpsertJob()),
              OrchestrationTrigger(resource.id.toString())
            )
          )
      }

    log.info("Started task ${taskRef.ref} to $description")
    return listOf(Task(id = taskRef.taskId, name = description))
  }

  override suspend fun delete(resource: Resource<ApplicationLoadBalancer>) {
    val description = "Delete load balancer ${resource.spec.moniker.name} in " +
      "${resource.spec.location.accountName}/${resource.spec.location.region}"

    val taskRef =
      resource.spec.let { spec ->
        orcaService
          .orchestrate(
            resource.serviceAccount,
            OrchestrationRequest(
              description,
              spec.moniker.app,
              description,
              listOf(spec.toDeleteJob()),
              OrchestrationTrigger(resource.id.toString())
            )
          )
      }

    log.info("Started task ${taskRef.ref} to $description")
  }

  override suspend fun actuationInProgress(id: ResourceId) =
    orcaService.getCorrelatedExecutions(id.value).isNotEmpty()

  private suspend fun CloudDriverService.getApplicationLoadBalancer(spec: ApplicationLoadBalancer, serviceAccount: String):
    ApplicationLoadBalancer? =
    try {
      getApplicationLoadBalancer(
        serviceAccount,
        CLOUD_PROVIDER,
        spec.location.accountName,
        spec.location.region,
        spec.moniker.name
      )
        .firstOrNull()
        ?.let { lb ->
          val securityGroupNames = lb.securityGroups.map {
            cloudDriverCache.securityGroupById(spec.location.accountName, spec.location.region, it).name
          }.toMutableSet()

          ApplicationLoadBalancer(
            moniker = if (lb.moniker != null) {
              Moniker(lb.moniker!!.app, lb.moniker!!.stack, lb.moniker!!.detail)
            } else {
              val parsedNamed = lb.loadBalancerName.split("-")
              Moniker(app = parsedNamed[0], stack = parsedNamed.getOrNull(1), detail = parsedNamed.getOrNull(2))
            },
            location = Location(
              accountName = spec.location.accountName,
              region = spec.location.region,
              availabilityZones = lb.availabilityZones,
              subnet = null
            ),
            loadBalancerType = LoadBalancerType.valueOf(lb.loadBalancerType.toUpperCase()),
            internal = lb.scheme != null && lb.scheme!!.contains("internal", ignoreCase = true),
            vpcName = lb.vpcId.let { cloudDriverCache.networkBy(it).name },
            subnetType = cloudDriverCache.subnetBy(lb.subnets.first()).purpose,
            listeners = lb.listeners.map { l ->
              ApplicationLoadBalancer.Listener(
                port = l.port,
                protocol = l.protocol,
                certificateArn = l.certificates?.firstOrNull()?.certificateArn,
                // TODO: filtering out default rules seems wrong, see TODO in ApplicationLoadBalancerNormalizer
                rules = l.rules.filter { !it.default }.toSet(),
                defaultActions = l.defaultActions.toSet()
              )
            }.toSet(),
            securityGroupNames = securityGroupNames,
            targetGroups = lb.targetGroups.map { tg ->
              ApplicationLoadBalancer.TargetGroup(
                name = tg.targetGroupName,
                targetType = tg.targetType,
                protocol = tg.protocol,
                port = tg.port,
                healthCheckEnabled = tg.healthCheckEnabled,
                healthCheckTimeoutSeconds = tg.healthCheckTimeoutSeconds,
                healthCheckPort = tg.healthCheckPort,
                healthCheckProtocol = tg.healthCheckProtocol,
                healthCheckHttpCode = tg.matcher.httpCode,
                healthCheckPath = tg.healthCheckPath,
                healthCheckIntervalSeconds = tg.healthCheckIntervalSeconds,
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

  private fun ApplicationLoadBalancer.toUpsertJob(): Job =
    Job(
      "upsertLoadBalancer",
      mapOf(
        "application" to moniker.app,
        "credentials" to location.accountName,
        "cloudProvider" to CLOUD_PROVIDER,
        "name" to moniker.name,
        "region" to location.region,
        "availabilityZones" to mapOf(location.region to location.availabilityZones),
        "loadBalancerType" to LoadBalancerType.APPLICATION.toString().toLowerCase(),
        "vpcId" to cloudDriverCache.networkBy(vpcName, location.accountName, location.region).id,
        "subnetType" to subnetType,
        "isInternal" to internal,
        "idleTimeout" to idleTimeout,
        "securityGroups" to securityGroupNames,
        "listeners" to listeners,
        "targetGroups" to targetGroups
      )
    )

  private fun ApplicationLoadBalancer.toDeleteJob(): Job =
    Job(
      "deleteLoadBalancer",
      mapOf(
        "application" to moniker.app,
        "credentials" to location.accountName,
        "cloudProvider" to CLOUD_PROVIDER,
        "loadBalancerName" to moniker.name,
        "loadBalancerType" to loadBalancerType,
        "regions" to listOf(location.region)
      )
    )
}
