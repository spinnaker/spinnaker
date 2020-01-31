package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.resources
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.failed
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.succeeded

abstract class DeliveryConfigRepositoryTests<T : DeliveryConfigRepository, R : ResourceRepository, A : ArtifactRepository> :
  JUnit5Minutests {

  abstract fun createDeliveryConfigRepository(resourceTypeIdentifier: ResourceTypeIdentifier): T
  abstract fun createResourceRepository(): R
  abstract fun createArtifactRepository(): A

  open fun flush() {}

  data class Fixture<T : DeliveryConfigRepository, R : ResourceRepository, A : ArtifactRepository>(
    val deliveryConfigRepositoryProvider: (ResourceTypeIdentifier) -> T,
    val resourceRepositoryProvider: () -> R,
    val artifactRepositoryProvider: () -> A,
    val deliveryConfig: DeliveryConfig = DeliveryConfig(
      name = "keel",
      application = "keel",
      serviceAccount = "keel@spinnaker"
    )
  ) {
    private val resourceTypeIdentifier: ResourceTypeIdentifier =
      object : ResourceTypeIdentifier {
        override fun identify(apiVersion: String, kind: String): Class<out ResourceSpec> {
          return when (kind) {
            "security-group" -> DummyResourceSpec::class.java
            "cluster" -> DummyResourceSpec::class.java
            else -> error("unsupported kind $kind")
          }
        }
      }

    internal val repository: T = deliveryConfigRepositoryProvider(resourceTypeIdentifier)
    private val resourceRepository: R = resourceRepositoryProvider()
    private val artifactRepository: A = artifactRepositoryProvider()

    fun getByName() = expectCatching {
      repository.get(deliveryConfig.name)
    }

    fun getByApplication() = expectCatching {
      repository.getByApplication(deliveryConfig.application)
    }

    fun delete() {
      repository.deleteByApplication(deliveryConfig.application)
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
              type = "manual-judgement",
              status = ConstraintStatus.PENDING
            )
          )
        }
      }
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
          .failed()
          .isA<NoSuchDeliveryConfigException>()
      }

      test("retrieving config by application returns an empty list") {
        getByApplication()
          .succeeded()
          .isEmpty()
      }
    }

    context("storing a delivery config with no artifacts or environments") {
      before {
        store()
      }

      test("the config can be retrieved by name") {
        getByName()
          .succeeded()
          .and {
            get { name }.isEqualTo(deliveryConfig.name)
            get { application }.isEqualTo(deliveryConfig.application)
          }
      }

      test("the config can be retrieved by application") {
        getByApplication()
          .succeeded()
          .hasSize(1)
          .first()
          .get(DeliveryConfig::name)
          .isEqualTo(deliveryConfig.name)
      }
    }

    context("storing a delivery config with artifacts and environments") {
      deriveFixture {
        copy(
          deliveryConfig = deliveryConfig.copy(
            artifacts = setOf(
              DebianArtifact(name = "keel", deliveryConfigName = deliveryConfig.name)
            ),
            environments = setOf(
              Environment(
                name = "test",
                resources = setOf(
                  resource(kind = "cluster"),
                  resource(kind = "security-group")
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
                  resource(kind = "cluster"),
                  resource(kind = "security-group")
                )
              )
            )
          )
        )
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
            .succeeded()
            .and {
              get { name }.isEqualTo(deliveryConfig.name)
              get { application }.isEqualTo(deliveryConfig.application)
            }
        }

        test("artifacts are attached when retrieved by name") {
          getByName()
            .succeeded()
            .get { artifacts }.isEqualTo(deliveryConfig.artifacts)
        }

        test("artifacts are attached when retrieved by application") {
          getByApplication()
            .succeeded()
            .first()
            .get { artifacts }.isEqualTo(deliveryConfig.artifacts)
        }

        test("environments are attached when retrieved by name") {
          getByName()
            .succeeded()
            .get { environments }
            .isEqualTo(deliveryConfig.environments)
        }

        test("environments are attached when retrieved by application") {
          getByApplication()
            .succeeded()
            .first()
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

        test("can retrieve the environment for the resources") {
          val environment = deliveryConfig.environments.first { it.name == "test" }
          val resource = environment.resources.random()

          getEnvironment(resource)
            .succeeded()
            .isEqualTo(environment)
        }

        test("can retrieve the manifest for the resources") {
          val resource = deliveryConfig.resources.random()

          getDeliveryConfig(resource)
            .succeeded()
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
            .succeeded()
            .get { environments }.hasSize(deliveryConfig.environments.size)
        }
      }

      context("creating and deleting an application") {
        before {
          store()
          delete()
        }

        test("delete application data successfully") {
          getByName()
            .failed()
        }
      }
    }
  }
}
