package com.netflix.spinnaker.keel.cli.commands

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.cli.ArgType.Int
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.random.Random

@Component
class DataGenCommand(
  val repository: KeelRepository
): Subcommand("datagen", "Generate test data") {
  companion object {
    const val TEST_APP = "datagen"
    const val TEST_STACK = "local"
    const val TEST_ACCT = "test"
    const val TEST_DEB_PACKAGE = "fakedeb"
    const val TEST_SVC_ACCT = "delivery-engineering@netflix.com"
    val TEST_ARTIFACT = DebianArtifact(
      deliveryConfigName = TEST_APP,
      name = TEST_DEB_PACKAGE,
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      reference = TEST_APP
    )
    private val HEX_CHAR_POOL : List<Char> = ('0'..'9') + ('a'..'f')
  }

  private val deliveryConfigCount by option(
    type = Int,
    fullName = "deliveryConfigs",
    shortName = "c",
    description = "Number of delivery configs to generate"
  ).default(1)


  private val artifactCount by option(
    type = Int,
    fullName = "artifacts",
    shortName = "a",
    description = "Number of artifact versions to generate"
  ).default(1)


  private val resourceCount by option(
    type = Int,
    fullName = "resources",
    shortName = "r",
    description = "Number of resources per delivery config"
  ).default(1)

  override fun execute() {
    println("Generating Keel test data: $deliveryConfigCount delivery config(s) with $resourceCount cluster(s) each, $artifactCount artifact version(s)")

    (1..artifactCount).forEach { index ->
      println("Writing artifact ${TEST_ARTIFACT.name} version 1.0.$index")
      repository.storeArtifactInstance(
        PublishedArtifact(
          name = TEST_ARTIFACT.name,
          type = TEST_ARTIFACT.type,
          version = "1.0.$index",
          createdAt = Instant.now(),
          gitMetadata = GitMetadata(commit = makeFakeCommitHash(), author = "joesmith", branch = "master"),
          buildMetadata = BuildMetadata(id = index, status = "SUCCEEDED")
        )
      )
    }

    (1..deliveryConfigCount).forEach { configIndex ->
      val configName = "${TEST_APP.capitalize()}$configIndex"
      val resources = (1..resourceCount).map { resourceIndex ->
        Resource(
          kind = EC2_CLUSTER_V1_1.kind,
          metadata = mapOf(
            "application" to configName,
            "id" to "${configName}TestCluster$resourceIndex"
          ),
          spec = ClusterSpec(
            moniker = Moniker(configName, TEST_STACK),
            artifactReference = TEST_APP,
            locations = SubnetAwareLocations(
              account = TEST_ACCT,
              regions = setOf(SubnetAwareRegionSpec("us-west-2")),
              subnet = "internal (vpc0)"
            )
          )
        )
      }

      val deliveryConfig = DeliveryConfig(
        application = configName,
        name = configName,
        serviceAccount = TEST_SVC_ACCT,
        artifacts = setOf(TEST_ARTIFACT),
        environments = setOf(
          Environment(
            name = "test",
            resources = resources.toSet()
          )
        )
      )

      println("Writing delivery config ${deliveryConfig.name} with $resourceCount cluster(s)")
      repository.upsertDeliveryConfig(deliveryConfig)

      (1..artifactCount).forEach { index ->
        println("Writing artifact version record for delivery config ${deliveryConfig.name}, environment test, version 1.0.$index")
        repository.markAsSuccessfullyDeployedTo(deliveryConfig, TEST_ARTIFACT, "1.0.$index", "test")
      }
    }

    println("Successfully generated test data. Happy testing!")
  }

  private fun makeFakeCommitHash() =
    (1..7)
      .map { i -> Random.nextInt(0, HEX_CHAR_POOL.size) }
      .map(HEX_CHAR_POOL::get)
      .joinToString("");
}
