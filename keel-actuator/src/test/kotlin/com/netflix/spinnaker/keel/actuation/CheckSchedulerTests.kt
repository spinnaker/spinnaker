package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import org.springframework.context.ApplicationEventPublisher

internal object CheckSchedulerTests : JUnit5Minutests {

  private val resourceRepository = mockk<ResourceRepository>()
  private val deliveryConfigRepository = mockk<DeliveryConfigRepository>()
  private val resourceActuator = mockk<ResourceActuator>(relaxUnitFun = true)
  private val environmentPromotionChecker = mockk<EnvironmentPromotionChecker>()
  private val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
  private val resources = listOf(
    resource(
      apiVersion = "ec2.$SPINNAKER_API_V1",
      kind = "security-group",
      id = "ec2:security-group:prod:ap-south-1:keel-sg",
      application = "keel"
    ),
    resource(
      apiVersion = "ec2.$SPINNAKER_API_V1",
      kind = "cluster",
      id = "ec2:cluster:prod:keel",
      application = "keel"
    )
  )

  fun tests() = rootContext<CheckScheduler> {
    fixture {
      CheckScheduler(
        resourceRepository = resourceRepository,
        deliveryConfigRepository = deliveryConfigRepository,
        resourceActuator = resourceActuator,
        environmentPromotionChecker = environmentPromotionChecker,
        resourceCheckMinAgeDuration = Duration.ofMinutes(5),
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
            resourceActuator.checkResource(resource)
          }
        }
      }
    }
  }
}
