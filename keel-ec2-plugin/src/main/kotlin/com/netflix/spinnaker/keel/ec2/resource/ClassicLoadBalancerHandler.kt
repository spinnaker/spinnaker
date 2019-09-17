package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerListener
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
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.HttpException

class ClassicLoadBalancerHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>
) : ResourceHandler<ClassicLoadBalancer> {
  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "classic-load-balancer",
    "classic-load-balancers"
  ) to ClassicLoadBalancer::class.java

  override suspend fun current(resource: Resource<ClassicLoadBalancer>): ClassicLoadBalancer? =
    cloudDriverService.getClassicLoadBalancer(resource.spec, resource.serviceAccount)

  override suspend fun upsert(
    resource: Resource<ClassicLoadBalancer>,
    resourceDiff: ResourceDiff<ClassicLoadBalancer>
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

  override suspend fun delete(resource: Resource<ClassicLoadBalancer>) {
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

  private fun CloudDriverService.getClassicLoadBalancer(spec: ClassicLoadBalancer, serviceAccount: String): ClassicLoadBalancer? =
    runBlocking {
      try {
        getClassicLoadBalancer(
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
                accountName = spec.location.accountName,
                region = spec.location.region,
                availabilityZones = lb.availabilityZones,
                subnet = null
              ),
              loadBalancerType = LoadBalancerType.valueOf(lb.loadBalancerType.toUpperCase()),
              internal = lb.scheme != null && lb.scheme!!.contains("internal", ignoreCase = true),
              healthCheck = lb.healthCheck.target,
              healthInterval = lb.healthCheck.interval,
              healthyThreshold = lb.healthCheck.healthyThreshold,
              unhealthyThreshold = lb.healthCheck.unhealthyThreshold,
              healthTimeout = lb.healthCheck.timeout,
              idleTimeout = lb.idleTimeout,
              vpcName = lb.vpcId.let { cloudDriverCache.networkBy(it).name },
              subnetType = cloudDriverCache.subnetBy(lb.subnets.first()).purpose,
              listeners = lb.listenerDescriptions.map {
                ClassicLoadBalancerListener(
                  externalProtocol = it.listener.protocol,
                  externalPort = it.listener.loadBalancerPort,
                  internalProtocol = it.listener.instanceProtocol,
                  internalPort = it.listener.instancePort,
                  sslCertificateId = it.listener.sslcertificateId
                )
              }.toSet(),
              securityGroupNames = securityGroupNames
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

  private fun ClassicLoadBalancer.toUpsertJob(): Job =
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
        "vpcId" to cloudDriverCache.networkBy(vpcName, location.accountName, location.region).id,
        "subnetType" to subnetType,
        "isInternal" to internal,
        "healthCheck" to healthCheck,
        "healthInterval" to healthInterval,
        "healthyThreshold" to healthyThreshold,
        "unhealthyThreshold" to unhealthyThreshold,
        "healthTimeout" to healthTimeout,
        "idleTimeout" to idleTimeout,
        "securityGroups" to securityGroupNames,
        "listeners" to listeners
      )
    )

  private fun ClassicLoadBalancer.toDeleteJob(): Job =
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
