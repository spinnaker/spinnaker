package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationPaused
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckResult
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.test.DummyArtifactVersionedResourceSpec
import com.netflix.spinnaker.keel.test.artifactVersionedResource
import com.netflix.spinnaker.keel.veto.Veto
import com.netflix.spinnaker.keel.veto.VetoEnforcer
import com.netflix.spinnaker.keel.veto.VetoResponse
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.exceptions.UserException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

internal class ResourceActuatorTests : JUnit5Minutests {

  class Fixture {
    val resourceRepository = InMemoryResourceRepository()
    val artifactRepository = mockk<ArtifactRepository>()
    val deliveryConfigRepository = mockk<DeliveryConfigRepository>()
    val diffFingerprintRepository = InMemoryDiffFingerprintRepository()
    val actuationPauser: ActuationPauser = mockk() {
      every { isPaused(any<String>()) } returns false
      every { isPaused(any<Resource<*>>()) } returns false
    }
    val plugin1 = mockk<ResourceHandler<DummyArtifactVersionedResourceSpec, DummyArtifactVersionedResourceSpec>>(relaxUnitFun = true)
    val plugin2 = mockk<ResourceHandler<DummyArtifactVersionedResourceSpec, DummyArtifactVersionedResourceSpec>>(relaxUnitFun = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    val veto = mockk<Veto>()
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
  }

  fun tests() = rootContext<Fixture> {

    fixture { Fixture() }

    before {
      every { plugin1.name } returns "plugin1"
      every { plugin1.supportedKind } returns SupportedKind(parseKind("plugin1/foo@v1"), DummyArtifactVersionedResourceSpec::class.java)
      every { plugin2.name } returns "plugin2"
      every { plugin2.supportedKind } returns SupportedKind(parseKind("plugin2/bar@v1"), DummyArtifactVersionedResourceSpec::class.java)
    }

    after {
      resourceRepository.dropAll()
    }

    context("a managed resource exists") {
      val resource = artifactVersionedResource(
        kind = parseKind("plugin1/foo@v1")
      )

      before {
        resourceRepository.store(resource)
        resourceRepository.appendHistory(ResourceCreated(resource))
      }

      context("the resource check is not vetoed") {
        before {
          every { veto.check(resource) } returns VetoResponse(true, "all")
        }

        context("management is paused for that resource") {
          before {
            resourceRepository.appendHistory(ResourceActuationPaused(resource))
            every { actuationPauser.isPaused(resource) } returns true
            runBlocking {
              subject.checkResource(resource)
            }
          }
          test("the resource is skipped") {
            coVerify(exactly = 0) { plugin1.desired(any()) }
            coVerify(exactly = 0) { plugin1.current(any()) }
            coVerify(exactly = 0) { plugin1.create(any(), any()) }
            coVerify(exactly = 0) { plugin1.update(any(), any()) }
            coVerify(exactly = 0) { plugin1.delete(any()) }
          }

          test("a telemetry event is published") {
            verify { publisher.publishEvent(ResourceCheckSkipped(resource.kind, resource.id, "ActuationPaused")) }
          }
        }

        context("the plugin is already actuating this resource") {
          before {
            coEvery { plugin1.actuationInProgress(resource) } returns true

            runBlocking {
              subject.checkResource(resource)
            }
          }

          test("the resource is not resolved") {
            coVerify(exactly = 0) { plugin1.desired(any()) }
            coVerify(exactly = 0) { plugin1.current(any()) }
          }

          test("the resource is not updated") {
            coVerify(exactly = 0) { plugin1.create(any(), any()) }
            coVerify(exactly = 0) { plugin1.update(any(), any()) }
            coVerify(exactly = 0) { plugin1.delete(any()) }
          }

          test("a telemetry event is published") {
            verify { publisher.publishEvent(ResourceCheckSkipped(resource.kind, resource.id, "ActuationInProgress")) }
          }
        }

        context("the plugin is not already actuating this resource") {
          before {
            coEvery { plugin1.actuationInProgress(resource) } returns false
          }

          context("the current state matches the desired state") {
            before {
              coEvery {
                plugin1.desired(resource)
              } returns DummyArtifactVersionedResourceSpec(data = "fnord")
              coEvery {
                plugin1.current(resource)
              } returns DummyArtifactVersionedResourceSpec(data = "fnord")
            }

            context("the resource previously had a delta") {
              before {
                resourceRepository.appendHistory(ResourceDeltaDetected(resource, emptyMap()))

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the resource is not updated") {
                coVerify(exactly = 0) { plugin1.create(any(), any()) }
                coVerify(exactly = 0) { plugin1.update(any(), any()) }
                coVerify(exactly = 0) { plugin1.delete(any()) }
              }

              test("only the relevant plugin is queried") {
                coVerify(exactly = 0) { plugin2.desired(any()) }
                coVerify(exactly = 0) { plugin2.current(any()) }
              }

              test("a telemetry event is published") {
                verify { publisher.publishEvent(ofType<ResourceDeltaResolved>()) }
              }
            }

            context("when there is an actuation launched event in history, check other events before publishing ResourceDeltaResolved") {
              before {
                resourceRepository.appendHistory(ResourceActuationLaunched(resource, plugin1.name, emptyList()))
                runBlocking {
                  subject.checkResource(resource)
                }
              }
              test("ResourceDeltaResolved was not published since the task is still running") {
                verify(exactly = 0) { publisher.publishEvent(ofType<ResourceDeltaResolved>()) }
              }

              context("the task was finished successfully") {
                before {
                  resourceRepository.appendHistory(ResourceTaskSucceeded(resource, emptyList()))
                  runBlocking {
                    subject.checkResource(resource)
                  }
                }
                test("a resource delta resolved event is published") {
                  verify { publisher.publishEvent(ofType<ResourceDeltaResolved>()) }
                }
              }

              context("the task was finished with an error") {
                before {
                  resourceRepository.appendHistory(ResourceTaskFailed(resource, "error", emptyList()))
                  runBlocking {
                    subject.checkResource(resource)
                  }
                }
                test("a resource delta resolved event is published") {
                  verify { publisher.publishEvent(ofType<ResourceDeltaResolved>()) }
                }
              }
            }

            context("the resource was already valid") {
              before {
                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the resource is not updated") {
                coVerify(exactly = 0) { plugin1.create(any(), any()) }
                coVerify(exactly = 0) { plugin1.update(any(), any()) }
                coVerify(exactly = 0) { plugin1.delete(any()) }
              }

              test("only the relevant plugin is queried") {
                coVerify(exactly = 0) { plugin2.desired(any()) }
                coVerify(exactly = 0) { plugin2.current(any()) }
              }

              test("a telemetry event is published") {
                verify { publisher.publishEvent(ofType<ResourceValid>()) }
              }
            }
          }

          context("the current state is missing") {
            before {
              coEvery { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
              coEvery { plugin1.current(resource) } returns null
              coEvery { plugin1.create(resource, any()) } returns listOf(Task(id = randomUID().toString(), name = "a task"))

              runBlocking {
                subject.checkResource(resource)
              }
            }

            test("the resource is created via the relevant handler") {
              coVerify { plugin1.create(resource, any()) }
            }

            test("a telemetry event is published") {
              verifySequence {
                publisher.publishEvent(ofType<ResourceMissing>())
                publisher.publishEvent(ofType<ResourceActuationLaunched>())
              }
            }
          }

          context("the current state is wrong") {
            before {
              coEvery { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec(data = "fnord")
              coEvery { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec()
              coEvery { plugin1.update(resource, any()) } returns listOf(Task(id = randomUID().toString(), name = "a task"))

              runBlocking {
                subject.checkResource(resource)
              }
            }

            test("the resource is updated") {
              coVerify { plugin1.update(eq(resource), any()) }
            }

            test("a telemetry event is published") {
              verify {
                publisher.publishEvent(ofType<ResourceDeltaDetected>())
                publisher.publishEvent(ofType<ResourceActuationLaunched>())
              }
            }
          }

          context("plugin throws an exception in current state resolution") {
            before {
              coEvery { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
              coEvery { plugin1.current(resource) } throws RuntimeException("o noes")

              runBlocking {
                subject.checkResource(resource)
              }
            }

            test("the resource is not updated") {
              coVerify(exactly = 0) { plugin1.update(any(), any()) }
            }

            test("an event is published with the wrapped exception") {
              val event = slot<ResourceCheckResult>()
              verify { publisher.publishEvent(capture(event)) }
              expectThat(event.captured)
                .isA<ResourceCheckError>()
                .get { exceptionType }
                .isEqualTo(CannotResolveCurrentState::class.java)
            }

            context("the exception is a resolution exception caused by user error") {
              before {
                coEvery { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
                coEvery { plugin1.current(resource) } throws UserException("bad, bad user!")

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the user exception is wrapped in the event") {
                val event = slot<ResourceCheckResult>()
                verify { publisher.publishEvent(capture(event)) }
                expectThat(event.captured)
                    .isA<ResourceCheckError>()
                    .get { exceptionType }
                    .isEqualTo(UserException::class.java)
              }
            }

            context("the exception is a resolution exception caused by system error") {
              before {
                coEvery { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
                coEvery { plugin1.current(resource) } throws SystemException("oopsies!")

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the system exception is wrapped in the event") {
                val event = slot<ResourceCheckResult>()
                verify { publisher.publishEvent(capture(event)) }
                expectThat(event.captured)
                    .isA<ResourceCheckError>()
                    .get { exceptionType }
                    .isEqualTo(SystemException::class.java)
              }
            }
          }

          context("plugin throws an unhandled exception in desired state resolution") {
            before {
              coEvery { plugin1.desired(resource) } throws RuntimeException("o noes")
              coEvery { plugin1.current(resource) } returns null

              runBlocking {
                subject.checkResource(resource)
              }
            }

            test("the resource is not updated") {
              coVerify(exactly = 0) { plugin1.update(any(), any()) }
            }

            test("a telemetry event is published with the wrapped exception") {
              val event = slot<ResourceCheckResult>()
              verify { publisher.publishEvent(capture(event)) }
              expectThat(event.captured)
                .isA<ResourceCheckError>()
                .get { exceptionType }
                .isEqualTo(CannotResolveDesiredState::class.java)
            }
          }

          context("plugin throws a transient dependency exception in desired state resolution") {
            before {
              coEvery { plugin1.desired(resource) } throws object : ResourceCurrentlyUnresolvable("o noes") {}
              coEvery { plugin1.current(resource) } returns null

              runBlocking {
                subject.checkResource(resource)
              }
            }

            test("the resource is not updated") {
              coVerify(exactly = 0) { plugin1.update(any(), any()) }
            }

            test("a telemetry event is published with detail of the problem") {
              val event = slot<ResourceCheckUnresolvable>()
              verify { publisher.publishEvent(capture(event)) }
              expectThat(event.captured)
                .isA<ResourceCheckUnresolvable>()
                .get { message }
                .isEqualTo("o noes")
            }
          }

          context("plugin throws an exception on resource update") {
            before {
              coEvery { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
              coEvery { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec(artifactVersion = "fnord-41.0")
              coEvery { plugin1.update(resource, any()) } throws RuntimeException("o noes")

              runBlocking {
                subject.checkResource(resource)
              }
            }

            // TODO: do we want to track the error in the resource history?
            test("detection of the delta is tracked in resource history") {
              verify { publisher.publishEvent(ofType<ResourceDeltaDetected>()) }
            }

            test("a telemetry event is published") {
              verify { publisher.publishEvent(ofType<ResourceCheckError>()) }
            }
          }
        }
      }

      context("the resource check is vetoed") {
        before {
          every { veto.check(resource) } returns VetoResponse(false, "aVeto")
          coEvery { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
          coEvery { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec()
          coEvery { plugin1.actuationInProgress(resource) } returns false

          runBlocking {
            subject.checkResource(resource)
          }
        }

        test("resource checking is skipped and resource is vetoed") {
          verify { publisher.publishEvent(ResourceCheckSkipped(resource.kind, resource.id, "aVeto")) }
          verify { publisher.publishEvent(ofType<ResourceActuationVetoed>()) }
        }
      }

      context("the artifact versioned resource is vetoed and the veto response has vetoArtifact set") {
        before {
          every { veto.check(resource) } returns VetoResponse(allowed = false, vetoName = "aVeto", vetoArtifact = true)
          coEvery { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
          coEvery { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec()
          coEvery { plugin1.actuationInProgress(resource) } returns false
          every { deliveryConfigRepository.deliveryConfigFor(any()) } returns DeliveryConfig(
            name = "fnord-manifest",
            application = "fnord",
            serviceAccount = "keel@spin",
            artifacts = setOf(DebianArtifact("fnord", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))),
            environments = setOf(Environment(name = "staging", resources = setOf(resource)))
          )
          runBlocking {
            subject.checkResource(resource)
          }
        }

        test("the desired artifact version is vetoed from the target environment") {
          verify { artifactRepository.markAsVetoedIn(any(), any(), "fnord-42.0", "staging", false) }
          verify { publisher.publishEvent(ofType<ResourceActuationVetoed>()) }
        }
      }
    }
  }
}
