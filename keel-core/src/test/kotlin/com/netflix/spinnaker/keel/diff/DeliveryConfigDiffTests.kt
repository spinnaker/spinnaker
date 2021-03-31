package com.netflix.spinnaker.keel.diff

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency.quiet
import com.netflix.spinnaker.keel.api.NotificationFrequency.verbose
import com.netflix.spinnaker.keel.api.NotificationType.slack
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.BranchFilter
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.test.artifactVersionedResource
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.locatableResource
import de.danielbechler.diff.selector.CollectionItemElementSelector
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class DeliveryConfigDiffTests : JUnit5Minutests {
  class Fixture {
    val resource = locatableResource()
    val resource2 = artifactVersionedResource()
    val resource3 = locatableResource()

    val deliveryConfig = deliveryConfig(resource)
    val deliveryConfig2 = deliveryConfig(resource2)


    val artifact = DockerArtifact(
      name = "docker-artifact",
      deliveryConfigName = "myconfig",
      from = ArtifactOriginFilter(branch = BranchFilter("master"))
    )

    val anotherArtifact = DockerArtifact(
      name = "docker-artifact",
      deliveryConfigName = "myconfig",
      reference = "feature-branch",
      from = ArtifactOriginFilter(branch = BranchFilter("feature"))
    )

    val test = Environment(
      name = "test",
      resources = setOf(resource, resource2),
      constraints = setOf(ManualJudgementConstraint())
    )
    val prod = Environment(
      name = "prod",
      resources = setOf(locatableResource(), locatableResource()),
      constraints = setOf(DependsOnConstraint("test")),
      notifications = setOf(NotificationConfig(type = slack, address = "#yo", frequency = quiet))
    )

    val deliveryConfig3 = DeliveryConfig(
      name = "myconfig",
      application = "myapp",
      artifacts = setOf(artifact),
      environments = setOf(test, prod),
      serviceAccount = "service-account@me",
      metadata = mapOf("ignore" to "me")
    )

    lateinit var diff: DefaultResourceDiff<DeliveryConfig>
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("can diff two delivery configs"){
      test("no diff if same") {
        diff = DefaultResourceDiff(deliveryConfig, deliveryConfig)
        expectThat(diff.hasChanges()).isFalse()
      }

      test("diff if different") {
        diff = DefaultResourceDiff(deliveryConfig, deliveryConfig2)
        expectThat(diff.hasChanges()).isTrue()
      }

      context("difference in artifacts") {
        modifyFixture {
          diff = DefaultResourceDiff(
            desired = deliveryConfig3.copy(artifacts = setOf(artifact, anotherArtifact)),
            current = deliveryConfig3
          )
        }

        test("is detected") {
          expectThat(diff.hasChanges()).isTrue()
          expectThat(diff.affectedRootPropertyNames).containsExactly("artifacts")
        }

        test("the only change is the added artifact") {
          expectThat(diff.children.first().isChanged).isTrue()
          expectThat(diff.children.first().getChild(CollectionItemElementSelector(anotherArtifact)).isAdded).isTrue()
        }
      }

      test("difference in metadata ignored") {
        diff = DefaultResourceDiff(
          deliveryConfig3,
          deliveryConfig3.copy(metadata = emptyMap())
        )
        expectThat(diff.hasChanges()).isFalse()
      }

      context("difference in resources") {
        modifyFixture {
          diff = DefaultResourceDiff(
            desired = deliveryConfig3,
            current = deliveryConfig3.copy(
              environments = setOf(
                test.copy(resources = test.resources + resource3),
                prod
              )
            )
          )
        }

        test("is detected") {
          expect {
            that(diff.hasChanges()).isTrue()
            that(diff.affectedRootPropertyNames).containsExactly("environments")
          }
        }

        test("the only change is the removed resource") {
          expect {
            val envDiff = diff.children.first()
            that(envDiff.childCount()).isEqualTo(1)
            val testEnvDiff = envDiff.getChild(CollectionItemElementSelector(test))
            that(testEnvDiff.childCount()).isEqualTo(2)
            that(testEnvDiff.getChild("resources").isChanged).isTrue()
            that(testEnvDiff.getChild("resourceIds").isChanged).isTrue()
            that(testEnvDiff.getChild("resources")
              .getChild(CollectionItemElementSelector(resource3)).isRemoved).isTrue()
          }
        }
      }

      test("difference in notifications detected") {
        val diff = DefaultResourceDiff(
          deliveryConfig3,
          deliveryConfig3.copy(environments = setOf(test, prod.copy(notifications = setOf(NotificationConfig(type = slack, address = "#yo", frequency = verbose)))))
        )
        expectThat(diff.hasChanges()).isTrue()
      }
    }
  }
}
