package com.netflix.spinnaker.keel.dgs

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.ConstraintRepository
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.constraints.AllowedTimesConstraintAttributes
import com.netflix.spinnaker.keel.constraints.AllowedTimesConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.ManualJudgementConstraintAttributes
import com.netflix.spinnaker.keel.constraints.ManualJudgementConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.OriginalSlackMessageDetail
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.TimeWindow
import com.netflix.spinnaker.keel.core.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.core.api.windowsNumeric
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.rest.dgs.ConstraintsDataLoader
import com.netflix.spinnaker.keel.rest.dgs.EnvironmentArtifactAndVersion
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue

class ConstraintsDataLoaderTests: JUnit5Minutests {

  class Fixture {
    val keelRepository: KeelRepository = mockk()
    val constraintRepository: ConstraintRepository = mockk()
    val artifactRepository: ArtifactRepository = mockk()

    val clock = MutableClock()

    val twEvaluator = AllowedTimesConstraintEvaluator(
      clock,
      mockk(relaxed = true),
      mockk(relaxed = true),
      constraintRepository,
      artifactRepository
    )
    val repo: ConstraintRepository = mockk(relaxed = true)
    val mjEvaluator = ManualJudgementConstraintEvaluator(
      repo,
      clock,
      mockk(relaxed = true)
    )

    val constraintEvaluators: List<ConstraintEvaluator<*>> = listOf(
      twEvaluator,
      mjEvaluator
    )

    val twConstraint = TimeWindowConstraint(
      windows = listOf(
        TimeWindow(
          days = "Monday-Tuesday,Thursday-Friday",
          hours = "09-16"
        )
      ),
      tz = "America/Los_Angeles"
    )
    val mjConstraint = ManualJudgementConstraint()

    val version = "1"

    val artifact = DebianArtifact("fnord", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))
    val environment = Environment(
      name = "test",
      constraints = setOf(twConstraint)
    )
    val deliveryConfig = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(environment)
    )

    val deliveryConfigMjConst = deliveryConfig.copy(environments = setOf(
      environment.copy(constraints = setOf(mjConstraint))
    ))

    val subject = ConstraintsDataLoader(keelRepository, constraintEvaluators)
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("constraints - no data") {
      before {
        every { keelRepository.constraintStateForEnvironments(any()) } returns emptyList()
      }

      test("no constraints") {
        val config = deliveryConfig()
        val artifactVersion = EnvironmentArtifactAndVersion("test","artifact", version)
        val states = subject.getConstraintsState(mutableSetOf(artifactVersion), config)
        expectThat(states).hasSize(1)
        expectThat(states[artifactVersion]?.isEmpty()).isTrue()
      }
    }

    context("only time window constraints") {
      context("no persisted data") {
        before {
          every { keelRepository.constraintStateForEnvironments(any()) } returns listOf()
        }

        test("generates state") {
          val artifactVersion = EnvironmentArtifactAndVersion(environment.name, artifact.reference, version)
          val states = subject.getConstraintsState(mutableSetOf(artifactVersion), deliveryConfig)
          val timeWindowState = states[artifactVersion]?.first()
          expect {
            that(states[artifactVersion]?.size).isEqualTo(1)
            that(timeWindowState).isNotNull()
            that(timeWindowState?.type).isEqualTo("allowed-times")
          }
        }
      }

      context("persisted data") {
        before {
          every { keelRepository.constraintStateForEnvironments(any()) } returns listOf(
            ConstraintState(
              deliveryConfig.name,
              environment.name,
              version,
              artifact.reference,
              "allowed-times",
              PASS,
              judgedBy = "Spinnaker",
              judgedAt = clock.instant(),
              attributes = AllowedTimesConstraintAttributes(
                twConstraint.windowsNumeric,
                twConstraint.tz,
                true
              )
            )
          )
        }

        test("does not generate data") {
          val artifactVersion = EnvironmentArtifactAndVersion(environment.name, artifact.reference, version)
          val states = subject.getConstraintsState(mutableSetOf(artifactVersion), deliveryConfig)
          val timeWindowState = states[artifactVersion]?.first()
          expect {
            that(states[artifactVersion]?.size).isEqualTo(1)
            that(timeWindowState).isNotNull()
            that(timeWindowState?.type).isEqualTo("allowed-times")
          }
        }
      }
    }

    context("mj constraint and time window constraint") {
      context("no summary saved") {
        before {
          every { keelRepository.constraintStateForEnvironments(any()) } returns listOf()
        }

        test("pending summary created") {
          val artifactVersion = EnvironmentArtifactAndVersion(environment.name, artifact.reference, version)
          val states = subject.getConstraintsState(mutableSetOf(artifactVersion), deliveryConfigMjConst)
          val mjState = states[artifactVersion]?.first()
          expect {
            that(states[artifactVersion]?.size).isEqualTo(1)
            that(mjState).isNotNull()
            that(mjState?.type).isEqualTo("manual-judgement")
            that(mjState?.status).isEqualTo(NOT_EVALUATED)
          }
        }
      }

      context("summary saved") {
        before {
          every { keelRepository.constraintStateForEnvironments(any()) } returns listOf(
            ConstraintState(
              deliveryConfigMjConst.name,
              environment.name,
              version,
              artifact.reference,
              "manual-judgement",
              OVERRIDE_PASS,
              judgedBy = "user@keel.io",
              judgedAt = clock.instant(),
              attributes = ManualJudgementConstraintAttributes(
                slackDetails = listOf(
                  OriginalSlackMessageDetail(
                    clock.toString(),
                    channel = "#channel",
                    artifactCandidate = PublishedArtifact("name", "type","version", "reference"),
                    deliveryArtifact = artifact,
                    targetEnvironment = "env"
                  )
                )
              )
            )
          )
        }

        test("no pending summary created, slack details removed") {
          val artifactVersion = EnvironmentArtifactAndVersion(environment.name, artifact.reference, version)
          val states = subject.getConstraintsState(mutableSetOf(artifactVersion), deliveryConfigMjConst)
          val mjState = states[artifactVersion]?.first()
          expect {
            that(states[artifactVersion]?.size).isEqualTo(1)
            that(mjState).isNotNull()
            that(mjState?.type).isEqualTo("manual-judgement")
            that(mjState?.status).isEqualTo(OVERRIDE_PASS)
            that(mjState?.attributes).isNull()
          }
        }
      }
    }
  }
}
