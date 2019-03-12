package com.netflix.spinnaker.keel.sqs

import com.netflix.spinnaker.config.SqsConfiguration
import com.netflix.spinnaker.keel.annealing.ResourceActuator
import com.netflix.spinnaker.keel.annealing.ResourceCheckQueue
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.boot.test.mock.mockito.MockBean
import strikt.api.Assertion
import strikt.api.expectThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest(
  classes = [LocalStackSqsConfiguration::class, SqsConfiguration::class],
  properties = [
    "sqs.enabled=true",
    "sqs.account-name=test",
    "sqs.queue-arn=arn:aws:sqs:ap-south-1:530822479253:keel-test-resource_check"
  ],
  webEnvironment = NONE
)
internal class SqsIntegrationTests {

  @MockBean
  lateinit var resourceActuator: ResourceActuator

  @Autowired
  lateinit var resourceCheckQueue: ResourceCheckQueue

  @Test
  fun `can enqueue a resource check`() {
    val name = ResourceName("ec2:cluster:prod:ap-south-1:keel")
    val apiVersion = SPINNAKER_API_V1
    val kind = "cluster"

    val latch = CountDownLatch(1)
    resourceActuator.stub {
      on { checkResource(name, apiVersion, kind) } doAnswer { latch.countDown() }
    }

    resourceCheckQueue.scheduleCheck(name, apiVersion, kind)

    expectThat(latch).countsDownWithin(1, SECONDS)
    verify(resourceActuator).checkResource(name, apiVersion, kind)
  }
}

fun Assertion.Builder<CountDownLatch>.countsDownWithin(timeout: Long, unit: TimeUnit) =
  assert("counts down to zero within $timeout ${unit.name.toLowerCase()}") {
    if (it.await(timeout, unit)) pass() else fail()
  }
