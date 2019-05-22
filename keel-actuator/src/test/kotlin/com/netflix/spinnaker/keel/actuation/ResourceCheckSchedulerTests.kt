package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

internal object ResourceCheckSchedulerTests : JUnit5Minutests {

  private val resourceRepository = mockk<ResourceRepository>()
  private val resourceActuator = mockk<ResourceActuator>(relaxUnitFun = true)
  private val resources = listOf(
    ResourceHeader(
      uid = randomUID(),
      name = ResourceName("ec2:security-group:prod:ap-south-1:keel-sg"),
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "security-group"
    ),
    ResourceHeader(
      uid = randomUID(),
      name = ResourceName("ec2:cluster:prod:ap-south-1:keel"),
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "cluster"
    )
  )

  fun tests() = rootContext<ResourceCheckScheduler> {
    fixture {
      ResourceCheckScheduler(
        resourceRepository = resourceRepository,
        resourceActuator = resourceActuator,
        resourceCheckMinAgeMinutes = 5,
        resourceCheckBatchSize = 2
      )
    }

    context("scheduler is disabled") {
      test("nothing happens") {
        checkResources()

        verify { resourceActuator wasNot Called }
      }
    }

    context("scheduler is enabled") {
      before {
        onApplicationUp()

        every {
          resourceRepository.nextResourcesDueForCheck(any(), any())
        } returns resources
      }

      after {
        onApplicationDown()
      }

      test("checks for all resources are scheduled") {
        checkResources()

        resources.forEach { (_, name, apiVersion, kind) ->
          verify {
            resourceActuator.checkResource(name, apiVersion, kind)
          }
        }
      }
    }
  }
}
