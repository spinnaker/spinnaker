package com.netflix.spinnaker.keel.front50

import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.caffeine.CacheProperties
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.kork.exceptions.SystemException
import dev.minutest.experimental.SKIP
import dev.minutest.experimental.minus
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.called
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.coEvery as every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectCatching
import strikt.assertions.isFailure
import io.mockk.coVerify as verify

class Front50CacheTests : JUnit5Minutests {
  class Fixture {
    private val cacheFactory = CacheFactory(mockk(relaxed = true), CacheProperties())
    private val appsByName = (1..10).associate { "app-$it" to Application("app-$it", "owner-$it@keel.io") }
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

      context("allApplications") {
        context("after priming") {
          before {
            subject.primeCaches()
          }

          test("retrieving all applications does not cause additional calls to Front50") {
            runBlocking {
              subject.allApplications()
              subject.allApplications()
            }

            verify(exactly = 1) {
              front50Service.allApplications(any())
            }
          }
        }

        // FIXME: can't figure out how to set up the mock here so that the cache is empty
        //  and a call to allApplications() triggers loading.
        SKIP - context("with failure loading cache") {
          modifyFixture {
            every {
              front50Service.allApplications(any())
            } throws SystemException("oh noes")
          }

          test("failure is bubbled up") {
            verify {
              front50Service.allApplications(any())
            }

            expectCatching {
              runBlocking {
                subject.allApplications()
              }
            }.isFailure()
          }
        }
      }
    }
  }
}