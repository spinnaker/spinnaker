package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isFalse

internal class ManualJudgementConstraintEvaluatorTests : JUnit5Minutests {
  object Fixture {
    val clock = MutableClock()
    val repository = mockk<KeelRepository>(relaxUnitFun = true)
    val publisher: ApplicationEventPublisher = mockk()
    val subject = ManualJudgementConstraintEvaluator(repository, clock, publisher)

    val configName = "my-config"
    val version = "1.1.1"
    val artifact = DockerArtifact("fnord", reference = "dockerfnord", deliveryConfigName = configName)

    val resource: Resource<DummyResourceSpec> = resource()
    val constraint = ManualJudgementConstraint(timeout = Duration.ofHours(1))
    val env = Environment(
      name = "test",
      constraints = setOf(constraint),
      resources = setOf(resource)
    )
    val config = DeliveryConfig(
      name = configName,
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(env)
    )

    val state = ConstraintState(
      deliveryConfigName = configName,
      environmentName = env.name,
      artifactVersion = version,
      type = "manual-judgement",
      status = ConstraintStatus.PENDING,
      createdAt = clock.instant()
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("evaluating state") {
      test("before timeout we don't pass") {
        val result = subject.canPromote(artifact, version, config, env, constraint, state)
        expectThat(result).isFalse()
        verify(exactly = 0) { repository.storeConstraintState(any()) }
      }

      test("after timeout we still don't pass but we persist a failure") {
        clock.tickHours(1)
        clock.tickMinutes(1)
        val result = subject.canPromote(artifact, version, config, env, constraint, state)
        expectThat(result).isFalse()
        verify(exactly = 1) { repository.storeConstraintState(any()) }
      }
    }
  }
}
