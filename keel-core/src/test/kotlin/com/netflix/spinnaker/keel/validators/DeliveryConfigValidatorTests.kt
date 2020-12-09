package com.netflix.spinnaker.keel.validators

import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.exceptions.DuplicateArtifactReferenceException
import com.netflix.spinnaker.keel.exceptions.DuplicateResourceIdException
import com.netflix.spinnaker.keel.exceptions.InvalidAppNameException
import com.netflix.spinnaker.keel.exceptions.InvalidArtifactReferenceException
import com.netflix.spinnaker.keel.exceptions.MissingEnvironmentReferenceException
import com.netflix.spinnaker.keel.test.DummyArtifactReferenceResourceSpec
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.TEST_API_V1
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isFailure

internal class DeliveryConfigValidatorTests : JUnit5Minutests {

  private val configName = "my-config"
  val artifact = DockerArtifact(name = "org/image", deliveryConfigName = configName)

  val subject = DeliveryConfigValidator()

  fun deliveryConfigValidatorTests() = rootContext<DeliveryConfigValidator> {
    fixture {
      DeliveryConfigValidator()
    }

    context("application name is not valid") {
      val submittedConfig = SubmittedDeliveryConfig(
        name = configName,
        application = "{{application.name}}",
        serviceAccount = "keel@spinnaker",
        artifacts = setOf(artifact),
        environments = setOf(
          SubmittedEnvironment(
            name = "test",
            resources = setOf(
              SubmittedResource(
                kind = TEST_API_V1.qualify("whatever"),
                spec = DummyResourceSpec("test", "im a twin", "keel")
              )
            ),
            constraints = emptySet()
          )
        )
      )
      test("an error is thrown") {
        expectCatching {
          subject.validate(submittedConfig)
        }.isFailure()
         .isA<InvalidAppNameException>()
      }
    }

    context("a delivery config with non-unique resource ids fails validation") {
      val submittedConfig = SubmittedDeliveryConfig(
        name = configName,
        application = "keel",
        serviceAccount = "keel@spinnaker",
        artifacts = setOf(artifact),
        environments = setOf(
          SubmittedEnvironment(
            name = "test",
            resources = setOf(
              SubmittedResource(
                kind = TEST_API_V1.qualify("whatever"),
                spec = DummyResourceSpec("test", "im a twin", "keel")
              )
            ),
            constraints = emptySet()
          ),
          SubmittedEnvironment(
            name = "prod",
            resources = setOf(
              SubmittedResource(
                kind = TEST_API_V1.qualify("whatever"),
                spec = DummyResourceSpec("test", "im a twin", "keel")
              )
            ),
            constraints = emptySet()
          )
        )
      )

      test("an error is thrown") {
        expectCatching {
          subject.validate(submittedConfig)
        }.isFailure()
          .isA<DuplicateResourceIdException>()
      }
    }

    context("a delivery config with non-unique artifact references errors fails validation") {
      // Two different artifacts with the same reference
      val artifacts = setOf(
        DockerArtifact(name = "org/thing-1", deliveryConfigName = configName, reference = "thing"),
        DockerArtifact(name = "org/thing-2", deliveryConfigName = configName, reference = "thing")
      )

      val submittedConfig = SubmittedDeliveryConfig(
        name = configName,
        application = "keel",
        serviceAccount = "keel@spinnaker",
        artifacts = artifacts,
        environments = setOf(
          SubmittedEnvironment(
            name = "test",
            resources = setOf(
              SubmittedResource(
                metadata = mapOf("serviceAccount" to "keel@spinnaker"),
                kind = TEST_API_V1.qualify("whatever"),
                spec = DummyResourceSpec(data = "o hai")
              )
            ),
            constraints = emptySet()
          )
        )
      )
      test("an error is thrown") {
        expectCatching {
          subject.validate(submittedConfig)
        }.isFailure()
          .isA<DuplicateArtifactReferenceException>()
      }
    }

    context("submitting delivery config with invalid environment name as a constraint") {
      val submittedConfig = SubmittedDeliveryConfig(
        name = configName,
        application = "keel",
        serviceAccount = "keel@spinnaker",
        artifacts = setOf(DockerArtifact(name = "org/thing-1", deliveryConfigName = configName, reference = "thing")),
        environments = setOf(
          SubmittedEnvironment(
            name = "test",
            resources = emptySet(),
            constraints = emptySet()
          ),
          SubmittedEnvironment(
            name = "test",
            resources = emptySet(),
            constraints = setOf(DependsOnConstraint(environment = "notARealEnvironment"))
          )
        )
      )

      test("an error is thrown") {
        expectCatching {
          subject.validate(submittedConfig)
        }.isFailure()
          .isA<MissingEnvironmentReferenceException>()
      }
    }

    context("delivery config with reference to non-existent artifact") {

      val submittedConfig = SubmittedDeliveryConfig(
        name = configName,
        application = "keel",
        serviceAccount = "keel@spinnaker",
        artifacts = setOf(DockerArtifact(name = "org/thing-1", deliveryConfigName = configName, reference = "thing")),
        environments = setOf(
          SubmittedEnvironment(
            name = "test",
            resources = setOf(
              SubmittedResource(
                kind = TEST_API_V1.qualify("whatever"),
                spec = DummyArtifactReferenceResourceSpec(artifactReference = "does-not-exist")
              )

            ),
            constraints = emptySet()
          )
        )
      )
      test("an error is thrown") {

        expectCatching {
          subject.validate(submittedConfig)
        }.isFailure()
          .isA<InvalidArtifactReferenceException>()
      }
    }
  }
}
