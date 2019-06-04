package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.NoImageFound
import com.netflix.spinnaker.keel.api.NoImageFoundForRegion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.UnsupportedStrategy
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ScalingProcess
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.api.ec2.cluster.Cluster
import com.netflix.spinnaker.keel.api.ec2.cluster.Dependencies
import com.netflix.spinnaker.keel.api.ec2.cluster.Health
import com.netflix.spinnaker.keel.api.ec2.cluster.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.api.ec2.cluster.Scaling
import com.netflix.spinnaker.keel.api.ec2.image.IdImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.JenkinsJobImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.LatestFromPackageImageProvider
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResolvedResource
import com.netflix.spinnaker.keel.plugin.ResourceConflict
import com.netflix.spinnaker.keel.plugin.ResourceDiff
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.retrofit.isNotFound
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import de.danielbechler.diff.node.DiffNode
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
  private val imageService: ImageService,
  private val dynamicConfigService: DynamicConfigService,
  private val clock: Clock,
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>
) : ResolvableResourceHandler<ClusterSpec, Cluster> {

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "cluster",
    "clusters"
  ) to ClusterSpec::class.java

  override fun generateName(spec: ClusterSpec) = ResourceName(
    "ec2:cluster:${spec.location.accountName}:${spec.location.region}:${spec.moniker.name}"
  )

  override fun resolve(resource: Resource<ClusterSpec>): ResolvedResource<Cluster> {
    val imageId = resolveImageId(resource.spec.launchConfiguration.imageProvider, resource.spec.location.region)
    return runBlocking {
      ResolvedResource(
        desired = desired(resource.spec, imageId),
        current = current(resource)
      )
    }
  }

  private fun resolveImageId(imageProvider: ImageProvider, region: String): String {
    when (imageProvider) {
      is IdImageProvider -> {
        return imageProvider.imageId
      }
      is LatestFromPackageImageProvider -> {
        val artifactName = imageProvider.deliveryArtifact.name
        val namedImage = runBlocking {
          imageService.getLatestNamedImage(
            artifactName,
            dynamicConfigService.getConfig<String>(String::class.java, "images.default-account", "test")
          )
        } ?: throw NoImageFound(artifactName)

        log.info("Image found for {}: {}", artifactName, namedImage)

        val amis = namedImage.amis[region] ?: throw NoImageFoundForRegion(artifactName, region)
        return amis.first() // todo eb: when are there multiple?
      }
      is JenkinsJobImageProvider -> {
        val namedImage = runBlocking {
          imageService.getNamedImageFromJenkinsInfo(
            imageProvider.packageName,
            dynamicConfigService.getConfig<String>(String::class.java, "images.default-account", "test"),
            imageProvider.buildHost,
            imageProvider.buildName,
            imageProvider.buildNumber
          )
        } ?: throw NoImageFound(imageProvider.packageName)

        log.info("Image found for {}: {}", imageProvider.packageName, namedImage)
        val amis = namedImage.amis[region] ?: throw NoImageFoundForRegion(imageProvider.packageName, region)
        return amis.first() // todo eb: when are there multiple?
      }
      else -> {
        throw UnsupportedStrategy(imageProvider::class.simpleName.orEmpty(), ImageProvider::class.simpleName.orEmpty())
      }
    }
  }

  private fun desired(spec: ClusterSpec, imageId: String): Cluster {
    return Cluster(
      moniker = spec.moniker,
      location = spec.location,
      launchConfiguration = spec.launchConfiguration.generateLaunchConfiguration(imageId),
      capacity = spec.capacity,
      dependencies = spec.dependencies,
      health = spec.health,
      scaling = spec.scaling,
      tags = spec.tags
    )
  }

  private fun current(resource: Resource<ClusterSpec>): Cluster? {
    return runBlocking {
      cloudDriverService.getCluster(resource.spec)
    }
  }

  override fun upsert(
    resource: Resource<ClusterSpec>,
    resourceDiff: ResourceDiff<Cluster>
  ): List<TaskRef> =
    runBlocking {
      val spec = resourceDiff.desired
      val job = when {
        resourceDiff.isCapacityOnly() -> spec.resizeServerGroupJob()
        else -> spec.createServerGroupJob()
      }

      log.info("Upserting cluster using task: {}", job)

      orcaService
        .orchestrate(OrchestrationRequest(
          "Upsert cluster ${spec.moniker.name} in ${spec.location.accountName}/${spec.location.region}",
          spec.moniker.app,
          "Upsert cluster ${spec.moniker.name} in ${spec.location.accountName}/${spec.location.region}",
          listOf(Job(job["type"].toString(), job)),
          OrchestrationTrigger(resource.metadata.name.toString())
        ))
        .await()
    }
    .also { log.info("Started task {} to upsert cluster", it.ref) }
      // TODO: ugleee
      .let { listOf(TaskRef(it.ref)) }

  override fun actuationInProgress(name: ResourceName) =
    runBlocking {
      orcaService.getCorrelatedExecutions(name.value).await()
    }.isNotEmpty()

  /**
   * @return `true` if the only changes in the diff are to capacity.
   */
  private fun ResourceDiff<Cluster>.isCapacityOnly(): Boolean =
    current != null && diff.affectedRootPropertyTypes.all { it == Capacity::class.java }

  private val DiffNode.affectedRootPropertyTypes: List<Class<*>>
    get() {
      val types = mutableListOf<Class<*>>()
      visitChildren { node, visit ->
        visit.dontGoDeeper()
        types += node.valueType
      }
      return types
    }

  private suspend fun Cluster.createServerGroupJob(): Map<String, Any?> =
    mutableMapOf(
      "application" to moniker.app,
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
        "app" to moniker.app,
        "stack" to moniker.stack,
        "detail" to moniker.detail,
        "cluster" to moniker.name
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
      ?: throw ResourceConflict("Could not find current server group for cluster ${moniker.name} in ${location.accountName} / ${location.region}")

  override fun delete(resource: Resource<ClusterSpec>) {
    TODO("not implemented")
  }

  private suspend fun CloudDriverService.getAncestorServerGroup(spec: Cluster): ClusterActiveServerGroup? =
    try {
      activeServerGroup(
        spec.moniker.app,
        spec.location.accountName,
        spec.moniker.name,
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

  private suspend fun CloudDriverService.getCluster(spec: ClusterSpec): Cluster? {
    try {
      return withContext(Dispatchers.Default) {
        activeServerGroup(
          spec.moniker.app,
          spec.location.accountName,
          spec.moniker.name,
          spec.location.region,
          CLOUD_PROVIDER
        )
          .await()
          .run {
            Cluster(
              moniker = Moniker(app = moniker.app, stack = moniker.stack, detail = moniker.detail),
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
      .filter { it !in setOf("nf-infrastructure", "nf-datacenter", moniker.app) }
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
