package com.netflix.spinnaker.keel.front50

import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.caffeine.CacheProperties
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.GitRepository
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
import com.netflix.spinnaker.kork.exceptions.SystemException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isFailure
import strikt.assertions.isTrue
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class Front50CacheTests : JUnit5Minutests {
  class Fixture {
    private val cacheFactory = CacheFactory(mockk(relaxed = true), CacheProperties())
    val appsByName = (1..10).associate {
      "app-$it" to Application(
        "app-$it",
        "owner@keel.io",
        repoType = "stash",
        repoProjectKey = "spinnaker",
        repoSlug = "keel-$it"
      )
    }
    val front50Service: Front50Service = mockk()
    val subject = Front50Cache(front50Service, cacheFactory)

    fun setupMocks() {
      every {
        front50Service.allApplications(any())
      } returns appsByName.values.take(9)

      every {
        front50Service.applicationByName(any(), any())
      } answers {
        appsByName[arg(0)] ?: throw SystemException("not found")
      }

      every {
        front50Service.searchApplications(any(), any())
      } answers {
        val name = arg<Map<String, String>>(0).entries.first().value
        listOfNotNull(appsByName[name])
      }

      every {
        front50Service.updateApplication(any(), any(), any())
      } answers {
        val updatedApp = arg<Application>(2)
        appsByName[arg(0)]?.copy(managedDelivery = updatedApp.managedDelivery) ?: throw SystemException("not found")
      }
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("application caches") {
      before {
        setupMocks()
      }

      context("priming the caches") {
        before {
          subject.primeCaches()
        }

        test("uses the same data to prime both caches") {
          verify(exactly = 1) {
            front50Service.allApplications(any())
          }
        }
      }

      context("applicationsByName") {
        context("after priming") {
          before {
            subject.primeCaches()
          }

          test("app cached during priming is not retrieved again") {
            runBlocking {
              subject.applicationByName("app-1")
            }

            verify(exactly = 0) {
              front50Service.applicationByName("app-1")
            }
          }

          test("individual app is retrieved on cache miss") {
            runBlocking {
              subject.applicationByName("app-10")
            }

            verify(exactly = 1) {
              front50Service.applicationByName("app-10")
            }
          }
        }

        test("failure to retrieve app is bubbled up") {
          expectCatching {
            subject.applicationByName("unknown-app")
          }.isFailure()
        }
      }

      context("applicationsBySearchParams") {
        test("app is cached by search params") {
          runBlocking {
            repeat(3) {
              subject.searchApplications(mapOf("name" to "app-1"))
            }
          }

          verify(exactly = 1) {
            front50Service.searchApplications(mapOf("name" to "app-1"))
          }
        }

        test("non-matching search params returns an empty list") {
          expectThat(
            runBlocking { subject.searchApplications(mapOf("name" to "no-match")) }
          ).isEmpty()
        }
      }

      context("toggle git integration") {
        test("Verify that we update the application by name cache") {
          val app = appsByName.values.first()
          runBlocking {
            subject.applicationByName(app.name)
          }
          runBlocking {
            subject.updateManagedDeliveryConfig(app.name, "keel", ManagedDeliveryConfig(importDeliveryConfig = true))
          }
          val cachedApp = runBlocking {
            subject.applicationByName(app.name)
          }
          verify(exactly = 1) {
            front50Service.applicationByName(any())
          }

          expectThat(cachedApp.managedDelivery.importDeliveryConfig).isTrue()
        }

        test("Verify that we clear the cache and fetch again") {
          val app = appsByName.values.first()
          runBlocking {
            subject.searchApplicationsByRepo(GitRepository(app.repoType!!, app.repoProjectKey!!, app.repoSlug!!))
          }
          runBlocking {
            subject.updateManagedDeliveryConfig(app.name, "keel", ManagedDeliveryConfig(importDeliveryConfig = true))
          }
          runBlocking {
            subject.searchApplicationsByRepo(GitRepository(app.repoType!!, app.repoProjectKey!!, app.repoSlug!!))
          }
          verify(exactly = 2) {
            front50Service.searchApplications(any())
          }


        }
      }
    }
  }
}
