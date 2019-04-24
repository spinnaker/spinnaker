package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.Cluster.Dependencies
import com.netflix.spinnaker.keel.api.ec2.Cluster.Health
import com.netflix.spinnaker.keel.api.ec2.Cluster.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.Cluster.Location
import com.netflix.spinnaker.keel.api.ec2.Cluster.Moniker
import com.netflix.spinnaker.keel.api.ec2.Cluster.Scaling
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.NamedImage
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResourceConflict
import com.netflix.spinnaker.keel.plugin.ResourceDiff
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.retrofit.isNotFound
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.path.NodePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.HttpException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ClusterHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val clock: Clock,
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>
) : ResourceHandler<Cluster> {

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "cluster",
    "clusters"
  ) to Cluster::class.java

  private val imageIdSuppliers = setOf(
    NamedImageImageIdSupplier(objectMapper)
  )

  override fun generateName(spec: Cluster) = ResourceName(
    "ec2:cluster:${spec.location.accountName}:${spec.location.region}:${spec.moniker.cluster}"
  )

  override fun current(resource: Resource<Cluster>) =
    runBlocking {
      cloudDriverService.getCluster(resource.spec)
    }

  private fun Resource<Cluster>.imageIdFromMetadata(): String? =
    packageResource()?.let { pkg ->
      imageIdSuppliers.find { it.supports(pkg) }?.imageIdForCluster(pkg, this.spec)
    }

  interface ImageIdSupplier {
    fun supports(r: Resource<*>): Boolean
    fun imageIdForCluster(r: Resource<*>, c: Cluster): String?
  }

  class NamedImageImageIdSupplier(private val objectMapper: ObjectMapper) : ImageIdSupplier {
    override fun supports(r: Resource<*>) =
      r.apiVersion == SPINNAKER_API_V1.subApi("ec2") && r.kind == "namedImage"

    override fun imageIdForCluster(r: Resource<*>, c: Cluster): String? =
      objectMapper
        .convertValue<NamedImage>(r.spec)
        .currentImage
        ?.amis
        ?.get(c.location.region)
        ?.firstOrNull()
  }

  private fun Resource<Cluster>.packageResource(): Resource<Map<String, Any?>>? =
    this.metadata.data["packageResource"]?.let { p ->
      objectMapper.convertValue(p)
    }

  private fun ResourceDiff<Cluster>?.shouldDeploy(imageId: String?) =
    imageId != null &&
      (this == null ||
      !this.isImageOnly() ||
      this.source.launchConfiguration.imageId != imageId)

  private suspend fun Resource<Cluster>.maybeCreateServerGroupWithDynamicImage(resourceDiff: ResourceDiff<Cluster>?): Map<String, Any?> {
    val imageId = this.imageIdFromMetadata()
    return if (resourceDiff.shouldDeploy(imageId)) {
      this.spec.createServerGroupJob() + mapOf("amiName" to imageId!!)
    } else {
      emptyMap()
    }
  }

  override fun upsert(resource: Resource<Cluster>, resourceDiff: ResourceDiff<Cluster>?): List<TaskRef> =
    runBlocking {
      val spec = resource.spec
      val job = when {
        resourceDiff.isCapacityOnly() -> spec.resizeServerGroupJob()
        spec.hasStaticImage -> spec.createServerGroupJob()
        !resourceDiff.isImageOnly() -> emptyMap()
        else -> resource.maybeCreateServerGroupWithDynamicImage(resourceDiff)
      }

      if (job.isNotEmpty()) {
        log.info("Upserting cluster using task: {}", job)

        orcaService
          .orchestrate(OrchestrationRequest(
            "Upsert cluster ${spec.moniker.cluster} in ${spec.location.accountName}/${spec.location.region}",
            spec.moniker.application,
            "Upsert cluster ${spec.moniker.cluster} in ${spec.location.accountName}/${spec.location.region}",
            listOf(Job(job["type"].toString(), job)),
            OrchestrationTrigger(resource.metadata.name.toString())
          ))
          .await()
      } else {
        null
      }
    }
      ?.also { log.info("Started task {} to upsert cluster", it.ref) }
      // TODO: ugleee
      .let { if (it == null) emptyList() else listOf(TaskRef(it.ref)) }

  /**
   * @return `true` if the only changes in the diff are to capacity.
   */
  private fun ResourceDiff<Cluster>?.isCapacityOnly(): Boolean =
    this != null && diff.affectedRootPropertyTypes.all { it == Capacity::class.java }

  private fun ResourceDiff<Cluster>?.isImageOnly(): Boolean =
    this != null && this.diff.imageChanged && this.diff.nodePaths.size == 1

  private val imageIdPath = NodePath.with("launchConfiguration", "imageId")

  private val DiffNode.imageChanged: Boolean
    get() = this.nodePaths.contains(imageIdPath)

  private val DiffNode.nodePaths: Set<NodePath>
    get() {
      val paths = mutableSetOf<NodePath>()
      visitChildren { node, _ ->
        if (!node.hasChildren()) {
          paths.add(node.path)
        }
      }
      return paths
    }

  private val DiffNode.affectedRootPropertyTypes: List<Class<*>>
    get() {
      val types = mutableListOf<Class<*>>()
      visitChildren { node, visit ->
        visit.dontGoDeeper()
        types += node.valueType
      }
      return types
    }

  private val Cluster.hasStaticImage: Boolean
    get() = this.launchConfiguration.imageId != null

  private suspend fun Cluster.createServerGroupJob(): Map<String, Any?> =
    mutableMapOf(
      "application" to moniker.application,
      "credentials" to location.accountName,
      // <things to do with the strategy>
      // TODO: this will be parameterizable ultimately
      "strategy" to "redblack",
      "delayBeforeDisableSec" to 0,
      "delayBeforeScaleDownSec" to 0,
      "maxRemainingAsgs" to 2,
      "rollback" to mapOf(
        "onFailure" to false
      ),
      "scaleDown" to false,
      // </things to do with the strategy>
      "capacity" to mapOf(
        "min" to capacity.min,
        "max" to capacity.max,
        "desired" to capacity.desired
      ),
      "targetHealthyDeployPercentage" to 100, // TODO: any reason to do otherwise?
      "cooldown" to health.cooldown.seconds,
      "enabledMetrics" to health.enabledMetrics,
      "healthCheckType" to health.healthCheckType.name,
      "healthCheckGracePeriod" to health.warmup.seconds,
      "instanceMonitoring" to launchConfiguration.instanceMonitoring,
      "ebsOptimized" to launchConfiguration.ebsOptimized,
      "iamRole" to launchConfiguration.iamRole,
      "terminationPolicies" to health.terminationPolicies.map(TerminationPolicy::name),
      "subnetType" to location.subnet,
      "availabilityZones" to mapOf(
        location.region to location.availabilityZones
      ),
      "keyPair" to launchConfiguration.keyPair,
      "suspendedProcesses" to scaling.suspendedProcesses,
      "securityGroups" to securityGroupIds,
      "stack" to moniker.stack,
      "freeFormDetails" to moniker.detail,
      "tags" to tags,
      "useAmiBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
      "copySourceCustomBlockDeviceMappings" to false, // TODO: any reason to do otherwise?
      "virtualizationType" to "hvm", // TODO: any reason to do otherwise?
      "moniker" to mapOf(
        "app" to moniker.application,
        "stack" to moniker.stack,
        "detail" to moniker.detail,
        "cluster" to moniker.cluster
      ),
      "amiName" to launchConfiguration.imageId,
      "reason" to "Diff detected at ${clock.instant().iso()}",
      "instanceType" to launchConfiguration.instanceType,
      "type" to "createServerGroup",
      "cloudProvider" to CLOUD_PROVIDER,
      "loadBalancers" to dependencies.loadBalancerNames,
      "targetGroups" to dependencies.targetGroups,
      "account" to location.accountName
    ).also { job ->
      cloudDriverService.getAncestorServerGroup(this)
        ?.let { ancestorServerGroup ->
          job["source"] = mapOf(
            "account" to location.accountName,
            "region" to location.region,
            "asgName" to ancestorServerGroup.asg.autoScalingGroupName
          )
          job["copySourceCustomBlockDeviceMappings"] = true
        }
    }

  private suspend fun Cluster.resizeServerGroupJob(): Map<String, Any?> =
    cloudDriverService.getAncestorServerGroup(this)
      ?.let { currentServerGroup ->
        mapOf(
          "type" to "resizeServerGroup",
          "capacity" to mapOf(
            "min" to capacity.min,
            "max" to capacity.max,
            "desired" to capacity.desired
          ),
          "cloudProvider" to currentServerGroup.cloudProvider,
          "credentials" to location.accountName,
          "moniker" to mapOf(
            "app" to currentServerGroup.moniker.app,
            "stack" to currentServerGroup.moniker.stack,
            "detail" to currentServerGroup.moniker.detail,
            "cluster" to currentServerGroup.moniker.cluster,
            "sequence" to currentServerGroup.moniker.sequence
          ),
          "region" to currentServerGroup.region,
          "serverGroupName" to currentServerGroup.asg.autoScalingGroupName
        )
      }
      ?: throw ResourceConflict("Could not find current server group for cluster ${moniker.cluster} in ${location.accountName} / ${location.region}")

  override fun delete(resource: Resource<Cluster>) {
    TODO("not implemented")
  }

  private suspend fun CloudDriverService.getAncestorServerGroup(spec: Cluster): ClusterActiveServerGroup? =
    try {
      activeServerGroup(
        spec.moniker.application,
        spec.location.accountName,
        spec.moniker.cluster,
        spec.location.region,
        CLOUD_PROVIDER
      )
        .await()
    } catch (e: HttpException) {
      if (e.isNotFound) {
        null
      } else {
        throw e
      }
    }

  private suspend fun CloudDriverService.getCluster(spec: Cluster): Cluster? {
    try {
      return withContext(Dispatchers.Default) {
        activeServerGroup(
          spec.moniker.application,
          spec.location.accountName,
          spec.moniker.cluster,
          spec.location.region,
          CLOUD_PROVIDER
        )
          .await()
          .run {
            Cluster(
              moniker = Moniker(moniker.app, moniker.stack, moniker.detail),
              location = Location(
                accountName,
                region,
                subnet,
                zones
              ),
              launchConfiguration = launchConfig.run {
                LaunchConfiguration(
                  imageId,
                  instanceType,
                  ebsOptimized,
                  iamInstanceProfile,
                  keyName,
                  instanceMonitoring.enabled,
                  ramdiskId.orNull()
                )
              },
              capacity = capacity.let { Capacity(it.min, it.max, it.desired) },
              dependencies = Dependencies(
                loadBalancerNames = loadBalancers,
                securityGroupNames = securityGroupNames,
                targetGroups = targetGroups
              ),
              health = Health(
                enabledMetrics = asg.enabledMetrics.map { Metric.valueOf(it) }.toSet(),
                cooldown = asg.defaultCooldown.let(Duration::ofSeconds),
                warmup = asg.healthCheckGracePeriod.let(Duration::ofSeconds),
                healthCheckType = asg.healthCheckType.let { HealthCheckType.valueOf(it) },
                terminationPolicies = asg.terminationPolicies.map { TerminationPolicy.valueOf(it) }.toSet()
              ),
              scaling = Scaling(
                suspendedProcesses = asg.suspendedProcesses.map { ScalingProcess.valueOf(it) }.toSet()
              ),
              tags = asg.tags.associateBy(Tag::key, Tag::value).filterNot { it.key in DEFAULT_TAGS }
            )
          }
      }
    } catch (e: HttpException) {
      if (e.isNotFound) {
        return null
      }
      throw e
    }
  }

  private val Cluster.securityGroupIds: Collection<String>
    get() = dependencies
      .securityGroupNames
      // no need to specify these as Orca will auto-assign them, also the application security group
      // gets auto-created so may not exist yet
      .filter { it !in setOf("nf-infrastructure", "nf-datacenter", moniker.application) }
      .map {
        cloudDriverCache.securityGroupByName(location.accountName, location.region, it).id
      }

  private val ClusterActiveServerGroup.subnet: String
    get() = asg.vpczoneIdentifier.substringBefore(",").let { subnetId ->
      cloudDriverCache
        .subnetBy(subnetId)
        .purpose ?: throw IllegalStateException("Subnet $subnetId has no purpose!")
    }

  private val ClusterActiveServerGroup.securityGroupNames: Set<String>
    get() = securityGroups.map {
      cloudDriverCache.securityGroupById(accountName, region, it).name
    }
      .toSet()

  private fun Instant.iso() =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME)

  companion object {
    // these tags are auto-applied by CloudDriver so we should not consider them in a diff as they
    // will never be specified as part of desired state
    private val DEFAULT_TAGS = setOf(
      "spinnaker:application",
      "spinnaker:stack",
      "spinnaker:details"
    )
  }
}

/**
 * Returns `null` if the string is empty or null. Otherwise returns the string.
 */
private fun String?.orNull(): String? = if (isNullOrBlank()) null else this
