package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isSuccess
import strikt.assertions.none

abstract class DeliveryConfigRepositoryTests<T : DeliveryConfigRepository, R : ResourceRepository, A : ArtifactRepository> :
  JUnit5Minutests {

  abstract fun createDeliveryConfigRepository(resourceSpecIdentifier: ResourceSpecIdentifier): T
  abstract fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier): R
  abstract fun createArtifactRepository(): A

  open fun flush() {}

  data class Fixture<T : DeliveryConfigRepository, R : ResourceRepository, A : ArtifactRepository>(
    val deliveryConfigRepositoryProvider: (ResourceSpecIdentifier) -> T,
    val resourceRepositoryProvider: (ResourceSpecIdentifier) -> R,
    val artifactRepositoryProvider: () -> A,
    val deliveryConfig: DeliveryConfig = DeliveryConfig(
      name = "keel",
      application = "keel",
      serviceAccount = "keel@spinnaker"
    )
  ) {
    private val resourceSpecIdentifier: ResourceSpecIdentifier =
      ResourceSpecIdentifier(
        kind<DummyResourceSpec>("ec2/security-group@v1"),
        kind<DummyResourceSpec>("ec2/cluster@v1")
      )

    internal val repository: T = deliveryConfigRepositoryProvider(resourceSpecIdentifier)
    private val resourceRepository: R = resourceRepositoryProvider(resourceSpecIdentifier)
    private val artifactRepository: A = artifactRepositoryProvider()

    fun getByName() = expectCatching {
      repository.get(deliveryConfig.name)
    }

    fun getByApplication() = expectCatching {
      repository.getByApplication(deliveryConfig.application)
    }

    fun store() {
      repository.store(deliveryConfig)
    }

    fun storeResources() {
      deliveryConfig.environments.flatMap { it.resources }.forEach {
        resourceRepository.store(it)
      }
    }

    fun storeArtifacts() {
      deliveryConfig.artifacts.forEach {
        artifactRepository.register(it)
      }
    }

    fun storeJudgements() {
      deliveryConfig.artifacts.forEach { art ->
        deliveryConfig.environments.forEach { env ->
          repository.storeConstraintState(
            ConstraintState(
              deliveryConfigName = deliveryConfig.name,
              environmentName = env.name,
              artifactVersion = "${art.name}-1.0.0",
              artifactReference = art.reference,
              type = "manual-judgement",
              status = ConstraintStatus.PENDING
            )
          )
        }
      }
    }

    fun queueConstraintApproval() {
      repository.queueAllConstraintsApproved(deliveryConfig.name, "staging", "keel-1.0.0", "keel")
    }

    fun getEnvironment(resource: Resource<*>) = expectCatching {
      repository.environmentFor(resource.id)
    }

    fun getDeliveryConfig(resource: Resource<*>) = expectCatching {
      repository.deliveryConfigFor(resource.id)
    }
  }

  fun tests() = rootContext<Fixture<T, R, A>>() {
    fixture {
      Fixture(
        deliveryConfigRepositoryProvider = this@DeliveryConfigRepositoryTests::createDeliveryConfigRepository,
        resourceRepositoryProvider = this@DeliveryConfigRepositoryTests::createResourceRepository,
        artifactRepositoryProvider = this@DeliveryConfigRepositoryTests::createArtifactRepository
      )
    }

    after {
      flush()
    }

    context("an empty repository") {
      test("retrieving config by name fails") {
        getByName()
          .isFailure()
          .isA<NoSuchDeliveryConfigException>()
      }

      test("retrieving config by application returns an empty list") {
        getByApplication()
          .isFailure()
          .isA<NoSuchDeliveryConfigException>()
      }
    }

    context("storing a delivery config with no artifacts or environments") {
      before {
        store()
      }

      test("the config can be retrieved by name") {
        getByName()
          .isSuccess()
          .and {
            get { name }.isEqualTo(deliveryConfig.name)
            get { application }.isEqualTo(deliveryConfig.application)
          }
      }

      test("the config can be retrieved by application") {
        getByApplication()
          .isSuccess()
          .get(DeliveryConfig::name)
          .isEqualTo(deliveryConfig.name)
      }
    }

    context("storing a delivery config with artifacts and environments") {
      deriveFixture {
        copy(
          deliveryConfig = deliveryConfig.copy(
            artifacts = setOf(
              DebianArtifact(
                name = "keel",
                deliveryConfigName = deliveryConfig.name,
                vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2"))
              )
            ),
            environments = setOf(
              Environment(
                name = "test",
                resources = setOf(
                  resource(kind = parseKind("ec2/cluster@v1")),
                  resource(kind = parseKind("ec2/security-group@v1"))
                )
              ),
              Environment(
                name = "staging",
                constraints = setOf(
                  DependsOnConstraint(
                    environment = "test"
                  ),
                  ManualJudgementConstraint()
                ),
                resources = setOf(
                  resource(kind = parseKind("ec2/cluster@v1")),
                  resource(kind = parseKind("ec2/security-group@v1"))
                )
              )
            )
          )
        )
      }

      context("updating an existing delivery config") {
        deriveFixture {
          storeArtifacts()
          storeResources()
          store()

          copy(deliveryConfig = deliveryConfig.copy(serviceAccount = "new-service-account@spinnaker.io"))
        }

        before {
          store()
        }

        test("the service account can be updated") {
          getByName()
            .isSuccess()
            .get { serviceAccount }
            .isEqualTo(deliveryConfig.serviceAccount)
        }
      }

      context("the environments did not previously exist") {
        before {
          storeArtifacts()
          storeResources()
          store()
          storeJudgements()
        }

        test("the config can be retrieved by name") {
          getByName()
            .isSuccess()
            .and {
              get { name }.isEqualTo(deliveryConfig.name)
              get { application }.isEqualTo(deliveryConfig.application)
            }
        }

        test("artifacts are attached when retrieved by name") {
          getByName()
            .isSuccess()
            .get { artifacts }.isEqualTo(deliveryConfig.artifacts)
        }

        test("artifacts are attached when retrieved by application") {
          getByApplication()
            .isSuccess()
            .get { artifacts }.isEqualTo(deliveryConfig.artifacts)
        }

        test("environments are attached when retrieved by name") {
          getByName()
            .isSuccess()
            .get { environments }
            .isEqualTo(deliveryConfig.environments)
        }

        test("environments are attached when retrieved by application") {
          getByApplication()
            .isSuccess()
            .get { environments }
            .isEqualTo(deliveryConfig.environments)
        }

        test("constraint states can be retrieved and updated") {
          val environment = deliveryConfig.environments.first { it.name == "staging" }
          val recentConstraintState = repository.constraintStateFor(deliveryConfig.name, environment.name)

          expectThat(recentConstraintState)
            .hasSize(1)

          expectThat(recentConstraintState.first().status)
            .isEqualTo(ConstraintStatus.PENDING)

          val constraint = recentConstraintState
            .first()
            .copy(status = ConstraintStatus.PASS)
          repository.storeConstraintState(constraint)
          val appConstraintState = repository.constraintStateFor(deliveryConfig.application)
          val updatedConstraintState = repository.constraintStateFor(deliveryConfig.name, environment.name)

          expectThat(appConstraintState)
            .contains(updatedConstraintState)
          expectThat(updatedConstraintState)
            .hasSize(1)
          expectThat(updatedConstraintState.first().status)
            .isEqualTo(ConstraintStatus.PASS)
        }

        // TODO: this should be removed when the artifactReference is made non-nullable in the repository methods
        test("backwards-compatibility: constraint state can be retrieved with missing artifact reference in the database") {
          val environment = deliveryConfig.environments.first { it.name == "staging" }
          val originalState = repository.getConstraintState(
            deliveryConfig.name, environment.name, "keel-1.0.0", "manual-judgement", "keel"
          )
          val stateWithNullReference = originalState!!.copy(artifactReference = null)
          repository.storeConstraintState(stateWithNullReference)
          val updatedState = repository.getConstraintState(
            deliveryConfig.name, environment.name, "keel-1.0.0", "manual-judgement", "keel"
          )
          expectThat(updatedState).isNotNull()
          expectThat(updatedState!!.artifactReference).isNull()
        }

        // TODO: this should be removed when the artifactReference is made non-nullable in the repository methods
        test("backwards-compatibility: constraint state can be retrieved without passing artifact reference") {
          val environment = deliveryConfig.environments.first { it.name == "staging" }
          // This only works currently because the artifactVersion contains the artifact name for debians and makes them
          // unique, but won't work for Docker.
          val constraintState = repository.getConstraintState(
            deliveryConfig.name, environment.name, "keel-1.0.0", "manual-judgement", null
          )
          expectThat(constraintState).isNotNull()
          expectThat(constraintState!!.artifactReference).isNotNull()
        }

        test("can queue constraint approvals") {
          queueConstraintApproval()
          expectThat(repository.getQueuedConstraintApprovals(deliveryConfig.name, "staging", "keel"))
            .isEqualTo(setOf("keel-1.0.0"))
        }

        test("can retrieve the environment for the resources") {
          val environment = deliveryConfig.environments.first { it.name == "test" }
          val resource = environment.resources.random()

          getEnvironment(resource)
            .isSuccess()
            .isEqualTo(environment)
        }

        test("can retrieve the manifest for the resources") {
          val resource = deliveryConfig.resources.random()

          getDeliveryConfig(resource)
            .isSuccess()
            .isEqualTo(deliveryConfig)
        }
      }

      context("environments already existed") {
        before {
          storeArtifacts()
          storeResources()
          store()
          store()
        }

        test("the environments are not duplicated") {
          getByName()
            .isSuccess()
            .get { environments }.hasSize(deliveryConfig.environments.size)
        }
      }

      context("deleting a delivery config") {
        before {
          store()
        }

        context("by application name") {
          test("deletes data successfully for known application") {
            repository.deleteByApplication(deliveryConfig.application)
            getByName()
              .isFailure()
          }

          test("throws exception for unknown application") {
            expectThrows<NoSuchDeliveryConfigException> {
              repository.deleteByApplication("notfound")
            }
          }
        }

        context("by delivery config name") {
          test("deletes data successfully for known delivery config") {
            repository.deleteByName(deliveryConfig.name)
            getByName()
              .isFailure()
          }

          test("throws exception for unknown delivery config") {
            expectThrows<NoSuchDeliveryConfigException> {
              repository.deleteByName("notfound")
            }
          }
        }
      }

      context("updating an existing delivery config to move a resource from one environment to another") {
        deriveFixture {
          storeArtifacts()
          storeResources()
          store()

          val movedResources = deliveryConfig
            .resources
            .filter { it.kind == parseKind("ec2/security-group@v1") }
            .toSet()
          val newEnvironments= deliveryConfig
            .environments
            .map {
              it.copy(resources = it.resources - movedResources)
            } + Environment(
            name = "infrastructure",
            resources = movedResources
          )

          copy(
            deliveryConfig = deliveryConfig.run {
              copy(environments = newEnvironments.toSet())
            }
          )
        }

        before {
          store()
        }

        test("the resources now appear in the environment they were moved to") {
          getByName()
            .isSuccess()
            .get { environments.first { it.name == "infrastructure" }.resources }
            .hasSize(2)
        }

        listOf("test", "staging").forEach { environmentName ->
          test("the resources no longer appear in the environments they were moved from") {
            getByName()
              .isSuccess()
              .get { environments.first { it.name == environmentName }.resources }
              .hasSize(1)
              .none {
                get { kind } isEqualTo parseKind("ec2/security-group@v1")
              }
          }
        }
      }
    }
  }
}
