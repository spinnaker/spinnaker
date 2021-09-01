package com.netflix.spinnaker.keel.scm

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Tag
import com.netflix.spinnaker.keel.api.artifacts.ArtifactOriginFilter
import com.netflix.spinnaker.keel.api.artifacts.branchName
import com.netflix.spinnaker.keel.api.persistence.KeelReadOnlyRepository
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.DataSources
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.notifications.DeliveryConfigImportFailed
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.scm.DeliveryConfigImportListener.Companion.CODE_EVENT_COUNTER
import com.netflix.spinnaker.keel.test.submittedResource
import com.netflix.spinnaker.keel.upsert.DeliveryConfigUpserter
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.TestContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.called
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.one
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class DeliveryConfigImportListenerTests : JUnit5Minutests {
  class Fixture {
    val keelReadOnlyRepository: KeelReadOnlyRepository = mockk()
    val deliveryConfigUpserter: DeliveryConfigUpserter = mockk()
    val importer: DeliveryConfigImporter = mockk()
    val front50Cache: Front50Cache = mockk()
    val scmUtils: ScmUtils = mockk()
    val springEnv: Environment = mockk()
    val notificationRepository: DismissibleNotificationRepository = mockk()
    val spectator: Registry = mockk()
    val clock = MutableClock()
    val eventPublisher: ApplicationEventPublisher = mockk()
    val subject = DeliveryConfigImportListener(
      keelReadOnlyRepository = keelReadOnlyRepository,
      deliveryConfigUpserter = deliveryConfigUpserter,
      deliveryConfigImporter = importer,
      notificationRepository = notificationRepository,
      front50Cache = front50Cache,
      scmUtils = scmUtils,
      springEnv = springEnv,
      spectator = spectator,
      eventPublisher = eventPublisher,
      clock = clock
    )

    val configuredApp = Application(
      name = "fnord",
      email = "keel@keel.io",
      repoType = "stash",
      repoProjectKey = "myorg",
      repoSlug = "myrepo",
      managedDelivery = ManagedDeliveryConfig(importDeliveryConfig = true),
      dataSources = DataSources(enabled = emptyList(), disabled = emptyList())
    )

    val notConfiguredApp = Application(
      name = "notfnord",
      email = "keel@keel.io",
      repoType = "stash",
      repoProjectKey = "myorg",
      repoSlug = "another-repo",
      dataSources = DataSources(enabled = emptyList(), disabled = emptyList())
    )

    val artifactFromMain = DockerArtifact(
      name = "myorg/myartifact",
      deliveryConfigName = "myconfig",
      reference = "myartifact-main",
      from = ArtifactOriginFilter(branch = branchName("main"))
    )

    val deliveryConfig = SubmittedDeliveryConfig(
      application = "fnord",
      name = "myconfig",
      serviceAccount = "keel@keel.io",
      artifacts = setOf(artifactFromMain),
      environments = setOf(
        SubmittedEnvironment(
          name = "test",
          resources = setOf(
            submittedResource()
          )
        )
      )
    )

    fun setupMocks() {
      every {
        springEnv.getProperty("keel.importDeliveryConfigs.enabled", Boolean::class.java, true)
      } returns true

      every {
        spectator.counter(any(), any<Iterable<Tag>>())
      } returns mockk {
        every {
          increment()
        } just runs
      }

      every {
        eventPublisher.publishEvent(any<Object>())
      } just runs

      every {
        front50Cache.searchApplicationsByRepo(any())
      } returns listOf(configuredApp, notConfiguredApp)

      every {
        importer.import(any<CodeEvent>(), any())
      } returns deliveryConfig

      every {
        deliveryConfigUpserter.upsertConfig(deliveryConfig, any())
      } returns Pair(deliveryConfig.toDeliveryConfig(), false)

      every {
        scmUtils.getDefaultBranch(any())
      } returns "main"

      every {
        notificationRepository.dismissNotification(any<Class<DeliveryConfigImportFailed>>(), any(), any(), any())
      } returns true

      every {
        scmUtils.getCommitLink(any())
      } returns "https://commit-link.org"

      every {
        keelReadOnlyRepository.isApplicationConfigured(any())
      } answers {
        firstArg<String>() == deliveryConfig.application
      }
    }
  }

  val commitEvent = CommitCreatedEvent(
    repoKey = "stash/myorg/myrepo",
    targetBranch = "main",
    commitHash = "1d52038730f431be19a8012f6f3f333e95a53772"
  )

  val prMergedEvent = PrMergedEvent(
    repoKey = "stash/myorg/myrepo",
    targetBranch = "main",
    commitHash = "1d52038730f431be19a8012f6f3f333e95a53772",
    pullRequestBranch = "pr1",
    pullRequestId = "23"
  )

  val commitEventForAnotherBranch = commitEvent.copy(targetBranch = "not-a-match")

  // matches repo for nonConfiguredApp
  val commitEventForAnotherRepo = commitEvent.copy(repoKey = "stash/myorg/another-repo")

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("an application is configured to retrieve the delivery config from source") {
      before {
        setupMocks()
      }

      listOf<CodeEvent>(commitEvent, prMergedEvent).map { event ->
        context("a commit event matching the repo and branch is received") {
          before {
            subject.handleCodeEvent(event)
          }

          test("the delivery config is imported from the commit in the event") {
            verify(exactly = 1) {
              importer.import(
                codeEvent = event,
                manifestPath = any()
              )
            }
          }

          test("the delivery config is created/updated") {
            verify {
              deliveryConfigUpserter.upsertConfig(deliveryConfig, any())
            }
          }

          test("notification was dismissed on successful import") {
            verify {
              notificationRepository.dismissNotification(
                any<Class<DeliveryConfigImportFailed>>(),
                deliveryConfig.application,
                event.targetBranch,
                any()
              )
            }
          }

          test("a successful delivery config retrieval is counted") {
            val tags = mutableListOf<Iterable<Tag>>()
            verify {
              spectator.counter(CODE_EVENT_COUNTER, capture(tags))
            }
            expectThat(tags).one {
              contains(DELIVERY_CONFIG_RETRIEVAL_SUCCESS.toTags())
            }
          }
        }
      }

      context("apps that are not on Managed Delivery yet") {
        before {
          every {
            front50Cache.searchApplicationsByRepo(any())
          } returns listOf(configuredApp.copy(name = "notConfiguredApp"))

          subject.handleCodeEvent(commitEvent)
        }

        testEventIgnored()
      }

      context("apps with custom manifest path") {
        val manifestPath = "custom/spinnaker.yml"
        before {
          every {
            front50Cache.searchApplicationsByRepo(any())
          } returns listOf(
            configuredApp.copy(
              managedDelivery = ManagedDeliveryConfig(
                importDeliveryConfig = true,
                manifestPath = manifestPath
              )
            ),
            notConfiguredApp
          )
        }

        test("importing the manifest from the correct path") {
          subject.handleCodeEvent(commitEvent)
          verify(exactly = 1) {
            importer.import(
              codeEvent = commitEvent,
              manifestPath = manifestPath
            )
          }
        }

      }

      context("a commit event NOT matching the app repo is received") {
        before {
          subject.handleCodeEvent(commitEventForAnotherRepo)
        }

        testEventIgnored()
      }

      context("a commit event NOT matching the app default branch is received") {
        before {
          subject.handleCodeEvent(commitEventForAnotherBranch)
        }

        testEventIgnored()
      }
    }

    context("an application is NOT configured to retrieve the delivery config from source") {
      before {
        setupMocks()
      }

      context("a commit event matching the repo and branch is received") {
        before {
          subject.handleCodeEvent(commitEventForAnotherRepo)
        }

        testEventIgnored()
      }
    }

    context("feature flag is disabled") {
      before {
        setupMocks()
      }

      context("a commit event matching the repo and branch is received") {
        modifyFixture {
          every {
            springEnv.getProperty("keel.importDeliveryConfigs.enabled", Boolean::class.java, true)
          } returns false
        }

        before {
          subject.handleCodeEvent(commitEventForAnotherRepo)
        }

        testEventIgnored()
      }
    }

    context("error scenarios") {
      before {
        setupMocks()
      }

      context("failure to retrieve delivery config") {
        modifyFixture {
          every {
            importer.import(commitEvent, manifestPath = any())
          } throws SystemException("oh noes!")
        }

        before {
          subject.handleCodeEvent(commitEvent)
        }

        test("a delivery config retrieval error is counted") {
          val tags = mutableListOf<Iterable<Tag>>()
          verify {
            spectator.counter(CODE_EVENT_COUNTER, capture(tags))
          }
          expectThat(tags).one {
            contains(DELIVERY_CONFIG_RETRIEVAL_ERROR.toTags())
          }
        }

        test("an event is published") {
          verify {
            eventPublisher.publishEvent(any<DeliveryConfigImportFailed>())
          }
        }
      }
    }
  }

  private fun TestContextBuilder<Fixture, Fixture>.testEventIgnored() {
    test("the event is ignored") {
      verify {
        importer wasNot called
      }
      verify {
        deliveryConfigUpserter wasNot called
      }
    }
  }
}
