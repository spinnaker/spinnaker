package com.netflix.spinnaker.keel.bakery.artifact

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.BaseLabel.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.StoreType.EBS
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.artifacts.BakedImage
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.igor.artifact.ArtifactService
import com.netflix.spinnaker.keel.persistence.BakedImageRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactCheckSkipped
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.slot
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.env.MockEnvironment
import strikt.api.Assertion
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import strikt.assertions.withFirst
import strikt.mockk.captured
import strikt.mockk.isCaptured
import java.time.Instant.now
import java.util.UUID.randomUUID
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

internal class ImageHandlerTests : JUnit5Minutests {

  internal class Fixture {
    val artifact = DebianArtifact(
      name = "keel",
      deliveryConfigName = "delivery-config",
      vmOptions = VirtualMachineOptions(
        baseLabel = RELEASE,
        baseOs = "xenial",
        regions = setOf("us-west-2", "us-east-1"),
        storeType = EBS
      )
    )
    val deliveryConfig = deliveryConfig(
      configName = artifact.deliveryConfigName!!,
      artifact = artifact
    )

    val appVersion = "${artifact.name}-0.161.0-h63.24d0843"
    val baseAmiName = "bionicbase-x86_64-202103092356-ebs"
    val repository = mockk<KeelRepository>(relaxUnitFun = true) {
      every { getDeliveryConfig(any()) } returns deliveryConfig
    }
    val igorService = mockk<ArtifactService>()
    val baseImageCache = mockk<BaseImageCache>()
    val bakedImageRepository = mockk<BakedImageRepository>()
    val imageService = mockk<ImageService>() {
      every { log } returns org.slf4j.LoggerFactory.getLogger(ImageService::class.java)
    }
    val pausedRepository: PausedRepository = mockk() {
      every { applicationPaused(any()) } returns false
    }
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val taskLauncher = mockk<TaskLauncher>()
    val handler = ImageHandler(
      repository,
      baseImageCache,
      bakedImageRepository,
      igorService,
      imageService,
      publisher,
      taskLauncher,
      BakeCredentials("keel@spinnaker.io", "keel"),
      pausedRepository,
      MockEnvironment()
    )

    val image = NamedImage(
      imageName = "$appVersion-$baseAmiName",
      attributes = emptyMap(),
      tagsByImageId = artifact.vmOptions.regions.associate {
        "ami-${it.hashCode()}" to mapOf(
          "appversion" to appVersion,
          "base_ami_name" to baseAmiName,
          "base_ami_id" to "ami-${randomUUID()}"
        )
      },
      accounts = setOf("test"),
      amis = artifact.vmOptions.regions.associateWith { listOf("ami-${it.hashCode()}") }
    )

    val artifactVersion =
      artifact.toArtifactVersion(appVersion.removePrefix("${artifact.name}-"))

    lateinit var handlerResult: Assertion.Builder<Result<Unit>>
    val bakeTask = slot<List<Map<String, Any?>>>()
    val bakeTaskUser = slot<String>()
    val bakeTaskApplication = slot<String>()
    val bakeTaskArtifact = slot<List<Map<String, Any?>>>()
    val bakeTaskParameters = slot<Map<String, Any>>()

    fun runHandler(artifact: DeliveryArtifact) {
      if (artifact is DebianArtifact) {
        every {
          taskLauncher.submitJob(
            capture(bakeTaskUser),
            capture(bakeTaskApplication),
            any(),
            any(),
            any(),
            any(),
            artifact.correlationId(artifactVersion.version),
            capture(bakeTask),
            capture(bakeTaskArtifact),
            capture(bakeTaskParameters)
          )
        } answers {
          Task(randomUUID().toString(), "baking new image for ${artifact.name}")
        }
      }

      handlerResult = expectCatching {
        handler.handle(artifact)
      }
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("the artifact is not a Debian") {
      before {
        runHandler(DockerArtifact(artifact.name, branch = "main"))
      }

      test("nothing happens") {
        verify { imageService wasNot Called }
        verify { igorService wasNot Called }
        verify { baseImageCache wasNot Called }
        verify { taskLauncher wasNot Called }
        verify { publisher wasNot Called }
      }
    }

    context("the application is paused") {
      before {
        every { pausedRepository.applicationPaused(any()) } returns true
        runHandler(artifact)
      }

      test("nothing happens") {
        verify { imageService wasNot Called }
        verify { igorService wasNot Called }
        verify { baseImageCache wasNot Called }
        verify { taskLauncher wasNot Called }
        verify { publisher wasNot Called }
      }
    }

    context("a bake is already running for the artifact") {
      before {
        every { repository.artifactVersions(artifact, any()) } returns listOf(artifactVersion)
        every {
          taskLauncher.correlatedTasksRunning(artifact.correlationId(artifactVersion.version))
        } returns true

        runHandler(artifact)
      }

      test("an event is published") {
        verify {
          publisher.publishEvent(
            ArtifactCheckSkipped(artifact.type, artifact.name, "ActuationInProgress")
          )
        }
      }

      test("nothing else happens") {
        verify { imageService wasNot Called }
        verify { igorService wasNot Called }
        verify { baseImageCache wasNot Called }
      }
    }

    context("no bake is currently running") {
      before {
        every {
          taskLauncher.correlatedTasksRunning(artifact.correlationId(artifactVersion.version))
        } returns false
      }

      context("the artifact is not registered") {
        before {
          every { repository.artifactVersions(artifact, any()) } throws NoSuchArtifactException(
            artifact
          )
          every { repository.isRegistered(artifact.name, artifact.type) } returns false
          every { igorService.getVersions(any(), any(), DEBIAN) } returns listOf(appVersion)

          runHandler(artifact)
        }

        test("it gets registered automatically") {
          verify { repository.register(artifact) }
        }

        test("an event gets published") {
          verify { publisher.publishEvent(ofType<ArtifactRegisteredEvent>()) }
        }
      }

      context("the artifact is registered") {
        before {
          every { repository.getDeliveryConfig(deliveryConfig.name) } returns deliveryConfig
        }

        context("there are no known versions for the artifact in the repository or in Igor") {
          before {
            every { repository.artifactVersions(artifact, any()) } returns emptyList()
            every { repository.isRegistered(artifact.name, artifact.type) } returns true
            every { igorService.getVersions(any(), any(), DEBIAN) } returns emptyList()

            runHandler(artifact)
          }

          test("we do actually go check in Igor") {
            verify {
              igorService.getVersions(
                artifact.name,
                artifact.statuses.map(ArtifactStatus::toString),
                DEBIAN
              )
            }
          }

          test("the handler completes successfully") {
            handlerResult.isSuccess()
          }

          test("no bake is launched") {
            expectThat(bakeTask).isNotCaptured()
          }
        }

        context("the base image is up-to-date") {
          before {
            every {
              baseImageCache.getBaseAmiName(
                artifact.vmOptions.baseOs,
                artifact.vmOptions.baseLabel,
              )
            } returns baseAmiName
          }

          context("the desired version is known") {
            before {
              every { repository.artifactVersions(artifact, any()) } returns listOf(artifactVersion)
            }

            context("we know we've baked this before") {
              before {
                every {
                  bakedImageRepository.getByArtifactVersion(appVersion, artifact)
                } returns BakedImage(
                  name = appVersion + "_" + baseAmiName,
                  baseLabel = artifact.vmOptions.baseLabel,
                  baseOs = artifact.vmOptions.baseOs,
                  vmType = artifact.vmOptions.storeType.name,
                  cloudProvider = "aws",
                  appVersion = appVersion,
                  baseAmiName = baseAmiName,
                  amiIdsByRegion = artifact.vmOptions.regions.associateWith { "ami=${randomUUID()}" },
                  timestamp = now()
                )

                runHandler(artifact)
              }

              test("we don't bother checking CloudDriver") {
                verify(exactly = 0) { imageService.getLatestNamedImage(any(), any(), any(), any()) }
              }

              test("no bake is launched") {
                expectThat(bakeTask).isNotCaptured()
              }
            }

            context("we don't think we have baked this before") {
              before {
                every {
                  bakedImageRepository.getByArtifactVersion(appVersion, artifact)
                } returns null
              }

              context("an AMI for the desired version and base image already exists") {
                before {
                  every {
                    imageService.getLatestNamedImage(
                      AppVersion.parseName(appVersion),
                      "test",
                      any(),
                      artifact.vmOptions.baseOs
                    )
                  } returns image

                  runHandler(artifact)
                }

                test("no bake is launched") {
                  expectThat(bakeTask).isNotCaptured()
                }
              }

              context("an AMI for the desired version does not exist") {
                before {
                  every {
                    imageService.getLatestNamedImage(
                      AppVersion.parseName(appVersion),
                      "test",
                      any(),
                      artifact.vmOptions.baseOs
                    )
                  } returns null

                  every { repository.getArtifactVersion(artifact, appVersion, null) } returns PublishedArtifact(
                    name = artifact.name,
                    reference = artifact.reference,
                    version = appVersion,
                    type = DEBIAN,
                    metadata = emptyMap()
                  )

                  runHandler(artifact)
                }

                test("a bake is launched") {
                  expectThat(bakeTask)
                    .isCaptured()
                    .captured
                    .hasSize(1)
                    .first()
                    .and {
                      get("type").isEqualTo("bake")
                      get("package").isEqualTo("${appVersion.replaceFirst('-', '_')}_all.deb")
                      get("baseOs").isEqualTo(artifact.vmOptions.baseOs)
                      get("baseLabel").isEqualTo(
                        artifact.vmOptions.baseLabel.toString().toLowerCase()
                      )
                      get("storeType").isEqualTo(
                        artifact.vmOptions.storeType.toString().toLowerCase()
                      )
                      get("regions").isEqualTo(artifact.vmOptions.regions)
                    }
                }

                test("authentication details are derived from the artifact's delivery config") {
                  expect {
                    that(bakeTaskUser).isCaptured().captured.isEqualTo(deliveryConfig.serviceAccount)
                    that(bakeTaskApplication).isCaptured().captured.isEqualTo(deliveryConfig.application)
                  }
                }

                test("the artifact details are attached and we default to the '_all' arch") {
                  expectThat(bakeTaskArtifact)
                    .isCaptured()
                    .captured
                    .hasSize(1)
                    .withFirst {
                      get("name") isEqualTo artifact.name
                      get("version") isEqualTo appVersion.removePrefix("${artifact.name}-")
                      get("reference") isEqualTo "/${appVersion.replaceFirst('-', '_')}_all.deb"
                    }
                }
              }

              context("ami for the desired version does not exist and we can pick the correct arch") {
                before {
                  every {
                    imageService.getLatestNamedImage(
                      AppVersion.parseName(appVersion),
                      "test",
                      any(),
                      artifact.vmOptions.baseOs
                    )
                  } returns null

                  every { repository.getArtifactVersion(artifact, appVersion, null) } returns PublishedArtifact(
                    name = artifact.name,
                    reference = artifact.reference,
                    version = appVersion,
                    type = DEBIAN,
                    metadata = mapOf("arch" to "amd64")
                  )

                  runHandler(artifact)
                }

                test("the artifact details are attached with the correct arch") {
                  expectThat(bakeTask)
                    .isCaptured()
                    .captured
                    .hasSize(1)
                    .first()
                    .and {
                      get("type").isEqualTo("bake")
                      get("package").isEqualTo("${appVersion.replaceFirst('-', '_')}_amd64.deb")
                    }
                }
              }

              context("an AMI exists, but it does not have all the regions we need") {
                before {
                  every {
                    imageService.getLatestNamedImage(
                      AppVersion.parseName(appVersion),
                      "test",
                      artifact.vmOptions.regions.first(),
                      artifact.vmOptions.baseOs
                    )
                  } returns image
                  every {
                    imageService.getLatestNamedImage(
                      AppVersion.parseName(appVersion),
                      "test",
                      not(artifact.vmOptions.regions.first()),
                      artifact.vmOptions.baseOs
                    )
                  } returns null

                  every { repository.getArtifactVersion(artifact, appVersion, null) } returns PublishedArtifact(
                    name = artifact.name,
                    reference = artifact.reference,
                    version = appVersion,
                    type = DEBIAN,
                    metadata = emptyMap()
                  )

                  runHandler(artifact)
                }

                test("a bake is launched for the missing regions") {
                  expectThat(bakeTask)
                    .isCaptured()
                    .captured
                    .hasSize(1)
                    .withFirst {
                      get("regions") isEqualTo setOf("us-east-1")
                    }
                }

                test("an event is triggered because we want to track region mismatches") {
                  verify {
                    publisher.publishEvent(ofType<ImageRegionMismatchDetected>())
                  }
                }
              }

              context("an AMI exists, but it has an older base image") {
                before {
                  val newerBaseAmiVersion = "nflx-base-5.380.0-h1234.8808866"
                  every {
                    baseImageCache.getBaseAmiName(
                      artifact.vmOptions.baseOs,
                      artifact.vmOptions.baseLabel,
                    )
                  } returns newerBaseAmiVersion

                  every { repository.artifactVersions(artifact, any()) } returns listOf(
                    artifactVersion
                  )

                  every {
                    imageService.getLatestNamedImage(any(), any(), any(), any())
                  } returns image

                  every { repository.getArtifactVersion(artifact, appVersion, null) } returns PublishedArtifact(
                    name = artifact.name,
                    reference = artifact.reference,
                    version = appVersion,
                    type = DEBIAN,
                    metadata = emptyMap()
                  )

                  runHandler(artifact)
                }

                test("a bake is launched") {
                  expectThat(bakeTask)
                    .isCaptured()
                    .captured
                    .hasSize(1)
                    .withFirst {
                      get("type") isEqualTo "bake"
                      get("baseOs") isEqualTo artifact.vmOptions.baseOs
                      get("baseLabel") isEqualTo artifact.vmOptions.baseLabel.toString()
                        .toLowerCase()
                    }
                }
              }
            }
          }
        }
      }
    }
  }

  fun <T : Any> Assertion.Builder<CapturingSlot<T>>.isNotCaptured(): Assertion.Builder<CapturingSlot<T>> =
    assertThat("did not capture a value") { !it.isCaptured }
}
