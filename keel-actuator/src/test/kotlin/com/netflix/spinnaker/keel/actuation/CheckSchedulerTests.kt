package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

internal object CheckSchedulerTests : JUnit5Minutests {

  private val resourceRepository = mockk<ResourceRepository>()
  private val deliveryConfigRepository = mockk<DeliveryConfigRepository>()
  private val resourceActuator = mockk<ResourceActuator>(relaxUnitFun = true)
  private val environmentPromotionChecker = mockk<EnvironmentPromotionChecker>()
  private val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
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

  fun tests() = rootContext<CheckScheduler> {
    fixture {
      CheckScheduler(
        resourceRepository = resourceRepository,
        deliveryConfigRepository = deliveryConfigRepository,
        resourceActuator = resourceActuator,
        environmentPromotionChecker = environmentPromotionChecker,
        resourceCheckMinAgeMinutes = 5,
        resourceCheckBatchSize = 2,
        publisher = publisher
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
          resourceRepository.itemsDueForCheck(any(), any())
        } returns resources
      }

      after {
        onApplicationDown()
      }

      test("all resources are checked") {
        checkResources()

        resources.forEach { resource ->
          coVerify(timeout = 500) {
            with(resource) {
              resourceActuator.checkResource(name, apiVersion, kind)
            }
          }
        }
      }
    }
  }
}
