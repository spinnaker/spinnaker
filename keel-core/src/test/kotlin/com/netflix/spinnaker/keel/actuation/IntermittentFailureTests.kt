package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryUnhappyVetoRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.VetoEnforcer
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher

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
    val resourceRepository = InMemoryResourceRepository()
    val artifactRepository = mockk<ArtifactRepository>()
    val deliveryConfigRepository = mockk<DeliveryConfigRepository>()
    val diffFingerprintRepository = InMemoryDiffFingerprintRepository()
    val actuationPauser: ActuationPauser = mockk() {
      coEvery { isPaused(any<String>()) } returns false
      coEvery { isPaused(any<Resource<*>>()) } returns false
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
    val vetoRepository = InMemoryUnhappyVetoRepository(clock)

    val dynamicConfigService: DynamicConfigService = mockk(relaxUnitFun = true) {
      every {
        // mimicking how a cluster is set up
        getConfig(Int::class.java, "veto.unhappy.max-diff-count", any())
      } returns 2
      every {
        // mimicking how a cluster is set up
        getConfig(String::class.java, "veto.unhappy.waiting-time", any())
      } returns "PT0S" // Duration.ZERO
    }

    val veto = UnhappyVeto(
      diffFingerprintRepository,
      vetoRepository,
      dynamicConfigService,
      "PT10M"
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
      Clock.systemUTC())
    val desired = DummyResourceSpec(data = "fnord")
    val current = DummyResourceSpec()
    val diff = DefaultResourceDiff(desired, current)
  }

  fun tests() = rootContext<Fixture> {

    fixture { Fixture() }

    after {
      resourceRepository.dropAll()
    }

    context("resource has a diff") {
      val resource: Resource<DummyResourceSpec> = resource(
        kind = parseKind("plugin1/foo@v1")
      )

      before {
        resourceRepository.store(resource)
        resourceRepository.appendHistory(ResourceCreated(resource))
        resourceRepository.appendHistory(ResourceValid(resource))
        coEvery { plugin1.actuationInProgress(resource) } returns false
        // diff has happened once
        diffFingerprintRepository.store(resource.id, diff)

        // current state is diff
        coEvery { plugin1.desired(resource) } returns desired
        coEvery { plugin1.current(resource) } returns current
        coEvery { plugin1.update(resource, any()) } returns listOf(Task(id = randomUID().toString(), name = "a task"))
      }
      test("diff seen second time, so update happens") {
        runBlocking { subject.checkResource(resource) }
        coVerify { plugin1.update(resource, any()) }
      }

      context("diff seen third time") {
        before {
          // diff has happened twice
          diffFingerprintRepository.store(resource.id, diff)
        }
        test("vetoed because diff seen 3 times, so no actuation") {
          runBlocking { subject.checkResource(resource) }
          coVerify(exactly = 0) { plugin1.update(resource, any()) }
        }

        context("then, resource is briefly in no diff state") {
          before {
            coEvery { plugin1.desired(resource) } returns desired
            coEvery { plugin1.current(resource) } returns desired
            runBlocking { subject.checkResource(resource) }
          }

          test("no diff means nothing happens") {
            coVerify(exactly = 0) { plugin1.update(resource, any()) }
          }

          context("then it gets the same diff again") {
            before {
              coEvery { plugin1.desired(resource) } returns desired
              coEvery { plugin1.current(resource) } returns current
            }
            test("update is launched") {
              runBlocking { subject.checkResource(resource) }
              coVerify { plugin1.update(resource, any()) }
            }
          }
        }
      }
    }
  }
}
