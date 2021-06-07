package com.netflix.spinnaker.keel.actuation

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.UnhappyVetoConfig
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.plugins.ActionDecision
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.enforcers.EnvironmentExclusionEnforcer
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.EnvironmentLeaseRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.UnhappyVetoRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.VetoEnforcer
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Duration
import java.time.Instant
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import org.springframework.core.env.Environment as SpringEnvironment

/**
 * This class tests a specific scenario:
 * (1) a resource has a diff which we cannot fix, so it is vetoed.
 * (2) that diff goes away (it was an intermittent failure of another system, maybe) and we say it's happy
 * (3) that same diff comes back, and we still try again because it went from happy to diff.
 *
 * We see this happen sometimes with resources because all systems fail sometimes. This test makes sure
 * we can recover from those failures.
 */
class IntermittentFailureTests : JUnit5Minutests {

  class Fixture {
    val resourceRepository = mockk<ResourceRepository>()
    val artifactRepository = mockk<ArtifactRepository>()
    val environmentLeaseRepository = mockk<EnvironmentLeaseRepository>(relaxUnitFun = true) {
      every { tryAcquireLease(any(), any(), any()) } returns mockk(relaxUnitFun = true)
    }
    val deliveryConfigRepository = mockk<DeliveryConfigRepository>()
    val diffFingerprintRepository = mockk<DiffFingerprintRepository>(relaxUnitFun = true)
    val actuationPauser: ActuationPauser = mockk() {
      every { isPaused(any<String>()) } returns false
      every { isPaused(any<Resource<*>>()) } returns false
    }
    val config = UnhappyVetoConfig()
    val springEnv: SpringEnvironment = mockk(relaxed = true) {
      every { getProperty("keel.events.diff-not-actionable.enabled", Boolean::class.java, any()) } returns true
      every { getProperty("keel.enforcement.environment-exclusion.enabled", Boolean::class.java, any()) } returns true
      io.mockk.every {
        getProperty("keel.unhappy.maxRetries", Int::class.java, any())
      } returns config.maxRetries
      io.mockk.every {
        getProperty("keel.unhappy.timeBetweenRetries", Duration::class.java, any())
      } returns config.timeBetweenRetries
    }
    val plugin1 = mockk<ResourceHandler<DummyResourceSpec, DummyResourceSpec>>(relaxUnitFun = true) {
      every { name } returns "plugin1"
      every { supportedKind } returns SupportedKind(parseKind("plugin1/foo@v1"), DummyResourceSpec::class.java)
    }
    val plugin2 = mockk<ResourceHandler<DummyResourceSpec, DummyResourceSpec>>(relaxUnitFun = true) {
      every { name } returns "plugin2"
      every { supportedKind } returns SupportedKind(parseKind("plugin2/bar@v1"), DummyResourceSpec::class.java)
    }
    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    val clock = MutableClock()
    val vetoRepository = mockk<UnhappyVetoRepository>(relaxUnitFun = true)

    val verificationRepository = mockk<ActionRepository>() {
      every { getVerificationContextsWithStatus(any(), any(), any()) }  returns emptyList()
    }

    val environmentExclusionEnforcer = EnvironmentExclusionEnforcer(springEnv, verificationRepository, artifactRepository, environmentLeaseRepository)

    val veto = UnhappyVeto(
      diffFingerprintRepository,
      vetoRepository,
      resourceRepository,
      springEnv,
      config,
      NoopRegistry(),
      clock
    )
    val vetoEnforcer = VetoEnforcer(listOf(veto))
    val subject = ResourceActuator(
      resourceRepository,
      artifactRepository,
      deliveryConfigRepository,
      diffFingerprintRepository,
      listOf(plugin1, plugin2),
      actuationPauser,
      vetoEnforcer,
      publisher,
      Clock.systemUTC(),
      environmentExclusionEnforcer,
      NoopRegistry()
    )
    val desired = DummyResourceSpec(data = "fnord")
    val current = DummyResourceSpec()
  }

  fun tests() = rootContext<Fixture> {

    fixture { Fixture() }

    context("resource has a diff") {
      val resource: Resource<DummyResourceSpec> = resource(
        kind = parseKind("plugin1/foo@v1")
      )

      before {
        every { resourceRepository.get(resource.id) } returns resource
        every { resourceRepository.lastEvent(resource.id) } returns ResourceValid(resource)

        every { deliveryConfigRepository.deliveryConfigFor(resource.id) } returns DeliveryConfig(
          name = "fnord-manifest",
          application = "fnord",
          serviceAccount = "keel@spin",
          artifacts = emptySet(),
          environments = setOf(Environment(name = "main", resources = setOf(resource)))
        )
        every { deliveryConfigRepository.deliveryConfigLastChecked(any()) } returns Instant.now()

        every { plugin1.actuationInProgress(resource) } returns false

        // current state is diff
        every { plugin1.desired(resource) } returns desired
        every { plugin1.current(resource) } returns current
        every { plugin1.update(resource, any()) } returns listOf(Task(id = randomUID().toString(), name = "a task"))
      }

      context("diff seen a second time") {
        before {
          // diff has happened twice
          every { diffFingerprintRepository.actionTakenCount(resource.id) } returns 2
        }

        context("resource is still in a diff state") {
          before {
            every { vetoRepository.getRecheckTime(resource) } returns clock.instant() + Duration.ofMinutes(20)

            runBlocking { subject.checkResource(resource) }
          }

          test("vetoed because diff has been seen and waiting time has not expired") {
            verify(exactly = 0) { plugin1.update(resource, any()) }
          }
        }

        context("then, resource is briefly in no diff state") {
          before {
            every { vetoRepository.getRecheckTime(resource) } returns clock.instant() - Duration.ofMinutes(1)

            every { plugin1.desired(resource) } returns desired
            every { plugin1.current(resource) } returns desired

            runBlocking { subject.checkResource(resource) }
          }

          test("no diff means nothing happens") {
            verify(exactly = 0) { plugin1.update(resource, any()) }
          }

          test("diff state is cleared") {
            verify { diffFingerprintRepository.clear(resource.id) }
          }
        }
      }
    }
  }
}
