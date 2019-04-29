package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.sync.Lock
import com.netflix.spinnaker.keel.telemetry.LockAttempt
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

internal object ResourceCheckSchedulerTests : JUnit5Minutests {

  private val resourceRepository = InMemoryResourceRepository()
  private val resourceCheckQueue = mockk<ResourceCheckQueue>(relaxUnitFun = true)
  private val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
  private val lock: Lock = mockk()
  private val resources = listOf(
    Resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "security-group",
      metadata = ResourceMetadata(
        name = ResourceName("ec2:security-group:prod:ap-south-1:keel-sg"),
        uid = randomUID()
      ),
      spec = "doesn't matter"
    ),
    Resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "cluster",
      metadata = ResourceMetadata(
        name = ResourceName("ec2:cluster:prod:ap-south-1:keel"),
        uid = randomUID()
      ),
      spec = "doesn't matter"
    )
  )

  fun tests() = rootContext<ResourceCheckScheduler> {
    fixture {
      ResourceCheckScheduler(resourceRepository, resourceCheckQueue, lock, publisher, 60_000)
    }

    before {
      resources.forEach(resourceRepository::store)
    }

    context("scheduler is disabled") {
      test("nothing happens") {
        checkManagedResources()

        verify { resourceCheckQueue wasNot Called }
      }
    }

    context("scheduler is enabled") {
      before {
        onApplicationUp()
      }

      after {
        onApplicationDown()
      }

      context("unable to acquire lock") {
        before {
          every { lock.tryAcquire(any(), any()) } returns false
        }

        test("nothing happens") {
          checkManagedResources()

          verify { resourceCheckQueue wasNot Called }
        }

        test("publishes a telemetry event") {
          verify { publisher.publishEvent(LockAttempt(false)) }
        }
      }

      context("able to acquire lock") {
        before {
          every { lock.tryAcquire(any(), any()) } returns true
        }

        test("checks for all resources are scheduled") {
          checkManagedResources()

          resources.forEach {
            verify { resourceCheckQueue.scheduleCheck(it) }
          }
        }

        test("publishes a telemetry event") {
          verify { publisher.publishEvent(LockAttempt(true)) }
        }
      }
    }
  }
}
