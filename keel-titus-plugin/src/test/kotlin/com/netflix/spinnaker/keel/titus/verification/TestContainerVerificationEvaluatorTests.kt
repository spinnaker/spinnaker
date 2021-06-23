package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.config.GitLinkConfig
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location
import com.netflix.spinnaker.keel.network.NetworkEndpoint
import com.netflix.spinnaker.keel.network.NetworkEndpointProvider
import com.netflix.spinnaker.keel.network.NetworkEndpointType.DNS
import com.netflix.spinnaker.keel.network.NetworkEndpointType.EUREKA_CLUSTER_DNS
import com.netflix.spinnaker.keel.network.NetworkEndpointType.EUREKA_VIP_DNS
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.titus.ContainerRunner
import com.netflix.spinnaker.keel.titus.deliveryConfigWithClusterAndLoadBalancer
import com.netflix.spinnaker.keel.titus.verification.TestContainerVerificationEvaluator.Companion.ENV_VAR_PREFIX
import de.huxhorn.sulky.ulid.ULID
import io.mockk.coEvery as every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsKeys
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import java.lang.IllegalStateException
import io.mockk.coVerify as verify

internal class TestContainerVerificationEvaluatorTests {

  private val context = ArtifactInEnvironmentContext(
    deliveryConfig = deliveryConfigWithClusterAndLoadBalancer(),
    environmentName = "test",
    artifactReference = "fnord",
    version = "1.1"
  )

  private val app = "fnord-test-app"

  private val loc = Location(
    account = "titustestvpc",
    region = "us-east-1"
  )

  private val verification = TestContainerVerification(
    image = "illuminati/fnord:latest",
    location = loc,
    application = app
  )

  private val publishedArtifact = PublishedArtifact(
    type = DOCKER,
    name = "fnord",
    version = "0.161.0-h61.116f116",
    reference = "my.docker.registry/fnord/fnord_0.161.0-h61.116f116",
    metadata = mapOf("buildNumber" to "61", "commitId" to "116f116"),
    provenance = "https://my.jenkins.master/jobs/fnord-release/60",
    buildMetadata = BuildMetadata(
      id = 61,
      number = "61",
      status = "BUILDING",
      uid = "just-a-uid-obviously"
    )
  ).normalized()

  private val containerRunner: ContainerRunner = mockk()

  private val keelRepository: KeelRepository = mockk {
    every { getArtifactVersion(any(), any(), any()) } returns publishedArtifact
  }

  private val eurekaClusterDns = "fnord-test-cluster.cluster.us-east-1.keel.io"
  private val eurekaVipDns = "fnord-test-cluster.vip.us-east-1.keel.io"
  private val albDns = "internal-fnord-test-alb-vpc0-1234567890.us-east-1.elb.amazonaws.com"

  private val endpointProvider: NetworkEndpointProvider = mockk {
    every {
      getNetworkEndpoints(any())
    } answers {
      when (arg<Resource<*>>(0).spec) {
        is TitusClusterSpec -> setOf(
          NetworkEndpoint(EUREKA_CLUSTER_DNS, "us-east-1", eurekaClusterDns),
          NetworkEndpoint(EUREKA_VIP_DNS, "us-east-1", eurekaVipDns),
        )
        is ApplicationLoadBalancerSpec -> setOf(
          NetworkEndpoint(DNS, "us-east-1", albDns)
        )
        else -> throw IllegalStateException("this is a bug in the test")
      }
    }
  }

  private val subject = TestContainerVerificationEvaluator(
    containerRunner = containerRunner,
    linkStrategy = null,
    gitLinkConfig = GitLinkConfig(),
    keelRepository = keelRepository,
    networkEndpointProvider = endpointProvider
  )

  @Test
  fun `starting verification launches a container job via containerRunner`() {
    val taskId = stubTaskLaunch()

    expectCatching { subject.start(context, verification) }
      .isSuccess()
      .get(TASKS)
      .isA<Iterable<String>>()
      .first() isEqualTo taskId

    val containerVars = slot<Map<String, String>>()

    verify {
      containerRunner.launchContainer(
        imageId = any(),
        subjectLine = any(),
        description = any(),
        serviceAccount = context.deliveryConfig.serviceAccount,
        application = any(),
        containerApplication = any(),
        environmentName = context.environmentName,
        location = verification.location,
        environmentVariables = capture(containerVars)
      )
    }
    expectThat(containerVars.captured).containsKeys(
      "${ENV_VAR_PREFIX}ENV",
      "${ENV_VAR_PREFIX}REPO_URL",
      "${ENV_VAR_PREFIX}BUILD_NUMBER",
      "${ENV_VAR_PREFIX}ARTIFACT_VERSION",
      "${ENV_VAR_PREFIX}BRANCH_NAME",
      "${ENV_VAR_PREFIX}COMMIT_SHA",
      "${ENV_VAR_PREFIX}COMMIT_URL",
      "${ENV_VAR_PREFIX}PR_NUMBER",
      "${ENV_VAR_PREFIX}PR_URL",
      "${ENV_VAR_PREFIX}EUREKA_VIP",
      "${ENV_VAR_PREFIX}EUREKA_CLUSTER",
      "${ENV_VAR_PREFIX}LOAD_BALANCER",
    )
  }

  @Test
  fun `endpoint information is correctly passed into the test container`() {
    stubTaskLaunch()
    subject.start(context, verification)

    val containerVars = slot<Map<String, String>>()
    verify {
      containerRunner.launchContainer(
        imageId = any(),
        subjectLine = any(),
        description = any(),
        serviceAccount = any(),
        application = any(),
        containerApplication = any(),
        environmentName = any(),
        location = any(),
        environmentVariables = capture(containerVars)
      )
    }

    expectThat(containerVars.captured["${ENV_VAR_PREFIX}EUREKA_VIP"]).isEqualTo(eurekaVipDns)
    expectThat(containerVars.captured["${ENV_VAR_PREFIX}EUREKA_CLUSTER"]).isEqualTo(eurekaClusterDns)
    expectThat(containerVars.captured["${ENV_VAR_PREFIX}LOAD_BALANCER"]).isEqualTo(albDns)
  }

  @Suppress("UNCHECKED_CAST")
  private fun verifyImageId(expectedImageId : String) {
    verify {
      containerRunner.launchContainer(
        imageId = match {
          it == expectedImageId
        },
        subjectLine = any(),
        description = any(),
        serviceAccount = any(),
        application = any(),
        containerApplication = any(),
        environmentName = any(),
        location = any(),
        environmentVariables = any()
      )
    }
  }

  @Test
  fun `image id specified by image field and tag`() {
    stubTaskLaunch()

    subject.start(context, TestContainerVerification(image="acme/rollerskates:rocket", location=loc, application=app))

    verifyImageId("acme/rollerskates:rocket")
  }

  @Test
  fun `image id specified by image field, no tag`() {
    stubTaskLaunch()

    subject.start(context, TestContainerVerification(image="acme/rollerskates", location=loc, application=app))

    verifyImageId("acme/rollerskates:latest")
  }

  @Suppress("UNCHECKED_CAST")
  private fun verifyApplication(expectedApplication : String) {
    verify {
      containerRunner.launchContainer(
        imageId = any(),
        subjectLine = any(),
        description = any(),
        serviceAccount = any(),
        application = any(),
        containerApplication = match {
          it == expectedApplication
        },
        environmentName = any(),
        location = any(),
        environmentVariables = any()
      )
    }
  }

  @Test
  fun `container job runs with verification's application`() {
    stubTaskLaunch()
    subject.start(context, verification)
    verifyApplication(verification.application!!)
  }

  @Test
  fun `if no application is specified container job runs with delivery config's application`() {
    stubTaskLaunch()
    subject.start(context, verification.copy(application = null))
    verifyApplication(context.deliveryConfig.application)
  }

  private fun stubTaskLaunch(): String =
    ULID()
      .nextULID()
      .also { taskId ->
        io.mockk.coEvery {
          containerRunner.launchContainer(
            imageId = any(),
            subjectLine = any(),
            description = any(),
            serviceAccount = any(),
            application = any(),
            environmentName = any(),
            location = any(),
            environmentVariables = any(),
            containerApplication = any(),
          )
        } answers { mapOf(TASKS to listOf(taskId)) }
      }
}
