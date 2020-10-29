package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.plugins.ActionDecision
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
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
import com.netflix.spinnaker.keel.events.ResourceDiffNotActionable
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionVetoed
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
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifySequence
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.time.Clock
import java.time.Duration
import java.time.Instant
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import org.springframework.core.env.Environment as SpringEnvironment

internal class ResourceActuatorTests : JUnit5Minutests {

  class Fixture {
    val resourceRepository = mockk<ResourceRepository>()
    val artifactRepository = mockk<ArtifactRepository>()
    val deliveryConfigRepository = mockk<DeliveryConfigRepository>()
    val diffFingerprintRepository = mockk<DiffFingerprintRepository>(relaxUnitFun = true)
    val actuationPauser: ActuationPauser = mockk() {
      every { isPaused(any<String>()) } returns false
      every { isPaused(any<Resource<*>>()) } returns false
    }
    val springEnv: SpringEnvironment = mockk(relaxed = true) {
      every { getProperty("keel.events.diff-not-actionable.enabled", Boolean::class.java, false) } returns true
    }
    val plugin1 = mockk<ResourceHandler<DummyArtifactVersionedResourceSpec, DummyArtifactVersionedResourceSpec>>(relaxUnitFun = true)
    val plugin2 = mockk<ResourceHandler<DummyArtifactVersionedResourceSpec, DummyArtifactVersionedResourceSpec>>(relaxUnitFun = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    val veto = mockk<Veto>()
    val vetoEnforcer = VetoEnforcer(listOf(veto))
    val clock = Clock.systemUTC()
    val subject = ResourceActuator(
      resourceRepository,
      artifactRepository,
      deliveryConfigRepository,
      diffFingerprintRepository,
      listOf(plugin1, plugin2),
      actuationPauser,
      vetoEnforcer,
      publisher,
      clock,
      springEnv
    )
  }

  fun tests() = rootContext<Fixture> {

    fixture { Fixture() }

    before {
      every { plugin1.name } returns "plugin1"
      every { plugin1.willTakeAction(any(), any()) } returns ActionDecision()
      every { plugin1.supportedKind } returns SupportedKind(parseKind("plugin1/foo@v1"), DummyArtifactVersionedResourceSpec::class.java)
      every { plugin2.name } returns "plugin2"
      every { plugin2.willTakeAction(any(), any()) } returns ActionDecision()
      every { plugin2.supportedKind } returns SupportedKind(parseKind("plugin2/bar@v1"), DummyArtifactVersionedResourceSpec::class.java)
    }

    context("a managed resource exists") {
      val resource = artifactVersionedResource(
        kind = parseKind("plugin1/foo@v1")
      )

      before {
        every { resourceRepository.lastEvent(resource.id) } returns ResourceCreated(resource)
        every { deliveryConfigRepository.deliveryConfigFor(resource.id) } returns DeliveryConfig(
          name = "fnord-manifest",
          application = "fnord",
          serviceAccount = "keel@spin",
          artifacts = setOf(DebianArtifact("fnord", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))),
          environments = setOf(Environment(name = "staging", resources = setOf(resource)))
        )
      }

      context("the resource check is not vetoed") {
        before {
          every { veto.check(resource) } returns VetoResponse(true, "all")
        }

        context("management is paused for that resource") {
          before {
            every { resourceRepository.lastEvent(resource.id) } returns ResourceActuationPaused(resource, "keel@keel.io")
            every { actuationPauser.isPaused(resource) } returns true
            runBlocking {
              subject.checkResource(resource)
            }
          }
          test("the resource is skipped") {
            verify(exactly = 0) { plugin1.desired(any()) }
            verify(exactly = 0) { plugin1.current(any()) }
            verify(exactly = 0) { plugin1.create(any(), any()) }
            verify(exactly = 0) { plugin1.update(any(), any()) }
            verify(exactly = 0) { plugin1.delete(any()) }
          }

          test("a telemetry event is published") {
            verify { publisher.publishEvent(ResourceCheckSkipped(resource.kind, resource.id, "ActuationPaused")) }
          }
        }

        context("the plugin is already actuating this resource") {
          before {
            every { plugin1.actuationInProgress(resource) } returns true

            runBlocking {
              subject.checkResource(resource)
            }
          }

          test("the resource is not resolved") {
            verify(exactly = 0) { plugin1.desired(any()) }
            verify(exactly = 0) { plugin1.current(any()) }
          }

          test("the resource is not updated") {
            verify(exactly = 0) { plugin1.create(any(), any()) }
            verify(exactly = 0) { plugin1.update(any(), any()) }
            verify(exactly = 0) { plugin1.delete(any()) }
          }

          test("a telemetry event is published") {
            verify { publisher.publishEvent(ResourceCheckSkipped(resource.kind, resource.id, "ActuationInProgress")) }
          }
        }

        context("the plugin is not already actuating this resource") {
          before {
            every { plugin1.actuationInProgress(resource) } returns false
          }

          context("the resource's delivery config has not been checked in a long time") {
            before {
              every { deliveryConfigRepository.deliveryConfigLastChecked(any()) } returns Instant.now().minus(Duration.ofDays(30))

              runBlocking {
                subject.checkResource(resource)
              }
            }

            test("the resource is not resolved") {
              verify(exactly = 0) { plugin1.desired(any()) }
              verify(exactly = 0) { plugin1.current(any()) }
            }

            test("the resource is not updated") {
              verify(exactly = 0) { plugin1.create(any(), any()) }
              verify(exactly = 0) { plugin1.update(any(), any()) }
              verify(exactly = 0) { plugin1.delete(any()) }
            }

            test("a telemetry event is published") {
              verify { publisher.publishEvent(ResourceCheckSkipped(resource.kind, resource.id, "PromotionCheckStale")) }
            }
          }

          context("the resource's delivery config was checked recently") {
            before {
              every { deliveryConfigRepository.deliveryConfigLastChecked(any()) } returns Instant.now().minus(Duration.ofSeconds(30))
            }

            context("the current state matches the desired state") {
              before {
                every {
                  plugin1.desired(resource)
                } returns DummyArtifactVersionedResourceSpec(data = "fnord")
                every {
                  plugin1.current(resource)
                } returns DummyArtifactVersionedResourceSpec(data = "fnord")
              }

              context("the resource previously had a delta") {
                before {
                  every { resourceRepository.lastEvent(resource.id) } returns ResourceDeltaDetected(resource, emptyMap())

                  runBlocking {
                    subject.checkResource(resource)
                  }
                }

                test("the resource is not updated") {
                  verify(exactly = 0) { plugin1.create(any(), any()) }
                  verify(exactly = 0) { plugin1.update(any(), any()) }
                  verify(exactly = 0) { plugin1.delete(any()) }
                }

                test("only the relevant plugin is queried") {
                  verify(exactly = 0) { plugin2.desired(any()) }
                  verify(exactly = 0) { plugin2.current(any()) }
                }

                test("a telemetry event is published") {
                  verify { publisher.publishEvent(ofType<ResourceDeltaResolved>()) }
                }
              }

              context("when there is an actuation launched event in history, check other events before publishing ResourceDeltaResolved") {
                before {
                  every { resourceRepository.lastEvent(resource.id) } returns ResourceActuationLaunched(resource, plugin1.name, emptyList())
                  runBlocking {
                    subject.checkResource(resource)
                  }
                }
                test("ResourceDeltaResolved was not published since the task is still running") {
                  verify(exactly = 0) { publisher.publishEvent(ofType<ResourceDeltaResolved>()) }
                }

                context("the task was finished successfully") {
                  before {
                    every { resourceRepository.lastEvent(resource.id) } returns ResourceTaskSucceeded(resource, emptyList())
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
                    every { resourceRepository.lastEvent(resource.id) } returns ResourceTaskFailed(resource, "error", emptyList())
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
                  verify(exactly = 0) { plugin1.create(any(), any()) }
                  verify(exactly = 0) { plugin1.update(any(), any()) }
                  verify(exactly = 0) { plugin1.delete(any()) }
                }

                test("only the relevant plugin is queried") {
                  verify(exactly = 0) { plugin2.desired(any()) }
                  verify(exactly = 0) { plugin2.current(any()) }
                }

                test("resource valid event is published") {
                  verify { publisher.publishEvent(ofType<ResourceValid>()) }
                }
              }
            }

            context("the current state is missing") {
              before {
                every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
                every { plugin1.current(resource) } returns null
                every { plugin1.create(resource, any()) } returns listOf(Task(id = randomUID().toString(), name = "a task"))

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the resource is created via the relevant handler") {
                verify { plugin1.create(resource, any()) }
              }

              test("resource history events are published") {
                verifySequence {
                  publisher.publishEvent(ofType<ResourceMissing>())
                  publisher.publishEvent(ofType<ResourceActuationLaunched>())
                }
              }
            }

            context("the current state is wrong") {
              before {
                every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec(data = "fnord")
                every { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec()
                every { plugin1.update(resource, any()) } returns listOf(Task(id = randomUID().toString(), name = "a task"))

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the resource is updated") {
                verify { plugin1.update(eq(resource), any()) }
              }

              test("resource history events are published") {
                verify {
                  publisher.publishEvent(ofType<ResourceDeltaDetected>())
                  publisher.publishEvent(ofType<ResourceActuationLaunched>())
                }
              }
            }

            context("plugin does not launch any tasks on update") {
              before {
                every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec(data = "fnord")
                every { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec()
                every { plugin1.update(resource, any()) } returns emptyList()

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the plugin is called to update the resource") {
                verify { plugin1.update(eq(resource), any()) }
              }

              test("resource delta detected event is published") {
                verify {
                  publisher.publishEvent(ofType<ResourceDeltaDetected>())
                }
              }

              test("resource actuation event is NOT published") {
                verify(exactly = 0) {
                  publisher.publishEvent(ofType<ResourceActuationLaunched>())
                }
              }
            }

            context("plugin throws an exception in current state resolution") {
              before {
                every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
                every { plugin1.current(resource) } throws RuntimeException("o noes")

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the resource is not updated") {
                verify(exactly = 0) { plugin1.update(any(), any()) }
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
                  every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
                  every { plugin1.current(resource) } throws UserException("bad, bad user!")

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
                  every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
                  every { plugin1.current(resource) } throws SystemException("oopsies!")

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
                every { plugin1.desired(resource) } throws RuntimeException("o noes")
                every { plugin1.current(resource) } returns null

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the resource is not updated") {
                verify(exactly = 0) { plugin1.update(any(), any()) }
              }

              test("a resource history event is published with the wrapped exception") {
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
                every { plugin1.desired(resource) } throws object : ResourceCurrentlyUnresolvable("o noes") {}
                every { plugin1.current(resource) } returns null

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              test("the resource is not updated") {
                verify(exactly = 0) { plugin1.update(any(), any()) }
              }

              test("a resource history event is published with detail of the problem") {
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
                every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
                every { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec(artifactVersion = "fnord-41.0")
                every { plugin1.update(resource, any()) } throws RuntimeException("o noes")

                runBlocking {
                  subject.checkResource(resource)
                }
              }

              // TODO: do we want to track the error in the resource history?
              test("detection of the delta is tracked in resource history") {
                verify { publisher.publishEvent(ofType<ResourceDeltaDetected>()) }
              }

              test("a resource history event is published") {
                verify { publisher.publishEvent(ofType<ResourceCheckError>()) }
              }
            }
          }
        }
      }

      context("the plugin can't resolve the diff") {
        before {
          every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
          every { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec(artifactName = "BLAH!BLAH!")
          every { plugin1.actuationInProgress(resource) } returns false
          every { veto.check(resource) } returns VetoResponse(allowed = true, vetoName = "aVeto")
          every { deliveryConfigRepository.deliveryConfigLastChecked(any()) } returns Instant.now().minus(Duration.ofSeconds(30))
          every { plugin1.willTakeAction(resource, any()) } returns ActionDecision(false, "i just can't right now")

          runBlocking {
            subject.checkResource(resource)
          }
        }

        test("no action is taken and event is emitted") {
          verify { publisher.publishEvent(ofType<ResourceDeltaDetected>()) }
          verify { publisher.publishEvent(ofType<ResourceDiffNotActionable>()) }
          verify(exactly = 0) { plugin1.create(any(), any()) }
          verify(exactly = 0) { plugin1.update(any(), any()) }
          verify(exactly = 0) { plugin1.delete(any()) }
        }
      }

      context("the resource check is vetoed") {
        before {
          every { veto.check(resource) } returns VetoResponse(false, "aVeto")
          every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
          every { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec()
          every { plugin1.actuationInProgress(resource) } returns false
          every { deliveryConfigRepository.deliveryConfigLastChecked(any()) } returns Instant.now().minus(Duration.ofSeconds(30))

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
          every { plugin1.desired(resource) } returns DummyArtifactVersionedResourceSpec()
          every { plugin1.current(resource) } returns DummyArtifactVersionedResourceSpec()
          every { plugin1.actuationInProgress(resource) } returns false
          every { deliveryConfigRepository.deliveryConfigLastChecked(any()) } returns Instant.now().minus(Duration.ofSeconds(30))
        }

        context("the version has not been deployed successfully before") {
          before {
            every { artifactRepository.wasSuccessfullyDeployedTo(any(), any(), any(), any()) } returns
              false
            every { artifactRepository.markAsVetoedIn(any(), any(), false) } returns true
            runBlocking {
              subject.checkResource(resource)
            }
          }
          test("the desired artifact version is vetoed from the target environment") {
            verify { artifactRepository.markAsVetoedIn(any(), any(), false) }
            verify { publisher.publishEvent(ofType<ResourceActuationVetoed>()) }
            verify { publisher.publishEvent(ofType<ArtifactVersionVetoed>()) }
          }
        }

        context("the version has previously been deployed successfully") {
          before {
            every { artifactRepository.wasSuccessfullyDeployedTo(any(), any(), any(), any()) } returns
              true
            runBlocking {
              subject.checkResource(resource)
            }
          }
          test("no version was vetoed") {
            verify(exactly = 0) { artifactRepository.markAsVetoedIn(any(), EnvironmentArtifactVeto("staging", "fnord", "fnord-42.0", "Spinnaker", "Automatically marked as bad because multiple deployments of this version failed."), false) }
            verify { publisher.publishEvent(ofType<ResourceActuationVetoed>()) }
          }
        }

      }
    }
  }
}
