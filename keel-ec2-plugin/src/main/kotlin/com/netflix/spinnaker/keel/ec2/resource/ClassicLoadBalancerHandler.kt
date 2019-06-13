package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancer
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerListener
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerType
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResourceDiff
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
    "clb",
    "clbs"
  ) to ClassicLoadBalancer::class.java

  override fun generateName(spec: ClassicLoadBalancer) = ResourceName(
    "ec2:loadBalancer:${spec.loadBalancerType}:${spec.location.accountName}:${spec.location.region}:" +
      spec.moniker.name
  )

  override suspend fun current(resource: Resource<ClassicLoadBalancer>): ClassicLoadBalancer? =
    cloudDriverService.getClassicLoadBalancer(resource.spec)

  override suspend fun upsert(
    resource: Resource<ClassicLoadBalancer>,
    resourceDiff: ResourceDiff<ClassicLoadBalancer>
  ): List<TaskRef> {
    val action = when {
      resourceDiff.current == null -> "Creating"
      else -> "Upserting"
    }

    val taskRef =
      resource.spec.let { spec ->
        orcaService
          .orchestrate(
            OrchestrationRequest(
              "$action ${spec.loadBalancerType.toString().toLowerCase()} load balancer ${spec.moniker.name} in " +
                "${spec.location.accountName}/${spec.location.region}",
              spec.moniker.app,
              "$action ${spec.loadBalancerType.toString().toLowerCase()} load balancer ${spec.moniker.name} in " +
                "${spec.location.accountName}/${spec.location.region}",
              listOf(spec.toCreateJob()),
              OrchestrationTrigger(resource.metadata.name.toString())
            )
          )
          .await()
      }

    log.info("Started task ${taskRef.ref} to create classicLoadBalancer ${resource.spec.moniker.name} in " +
      "${resource.spec.location.accountName}/${resource.spec.location.region}")
    return listOf(TaskRef(taskRef.ref))
  }

  override suspend fun delete(resource: Resource<ClassicLoadBalancer>) {
    val taskRef =
      resource.spec.let { spec ->
        orcaService
          .orchestrate(
            OrchestrationRequest(
              "Delete load balancer ${spec.moniker.name} in ${spec.location.accountName}/${spec.location.region}",
              spec.moniker.app,
              "Delete load balancer ${spec.moniker.name} in ${spec.location.accountName}/${spec.location.region}",
              listOf(spec.toDeleteJob()),
              OrchestrationTrigger(resource.metadata.name.toString())
            )
          )
          .await()
      }

    log.info("Started task ${taskRef.ref} to delete classicLoadBalancer ${resource.spec.moniker.name} in " +
      "${resource.spec.location.accountName}/${resource.spec.location.region}")
  }

  override suspend fun actuationInProgress(name: ResourceName) =
    orcaService.getCorrelatedExecutions(name.value).await().isNotEmpty()

  private fun CloudDriverService.getClassicLoadBalancer(spec: ClassicLoadBalancer): ClassicLoadBalancer? =
    runBlocking {
      try {
        getClassicLoadBalancer(
          CLOUD_PROVIDER,
          spec.location.accountName,
          spec.location.region,
          spec.moniker.name
        )
          .await()
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
              isInternal = lb.scheme != null && lb.scheme!!.contains("internal", ignoreCase = true),
              healthCheck = lb.healthCheck.target,
              healthInterval = lb.healthCheck.interval,
              healthyThreshold = lb.healthCheck.healthyThreshold,
              unhealthyThreshold = lb.healthCheck.unhealthyThreshold,
              healthTimeout = lb.healthCheck.timeout,
              idleTimeout = lb.idleTimeout,
              vpcName = lb.vpcid.let { cloudDriverCache.networkBy(it).name },
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

  private fun ClassicLoadBalancer.toCreateJob(): Job =
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
        "isInternal" to isInternal,
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
