package com.netflix.spinnaker.keel.annealing.spring

import com.netflix.spinnaker.config.AnnealingConfiguration
import com.netflix.spinnaker.keel.annealing.ResourceActuator
import com.netflix.spinnaker.keel.annealing.ResourceCheckQueue
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.nhaarman.mockito_kotlin.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [AnnealingConfiguration::class]
)
class SpringBasedResourceCheckQueueTests {

  @Autowired
  lateinit var resourceCheckQueue: ResourceCheckQueue

  @MockBean
  lateinit var resourceActuator: ResourceActuator

  @Test
  fun `can enqueue a resource check via Spring`() {
    val parameters = Triple(ResourceName("ec2:cluster:prod:ap-south-1:keel"), SPINNAKER_API_V1, "cluster")

    parameters.let { (name, apiVersion, kind) ->
      resourceCheckQueue.scheduleCheck(name, apiVersion, kind)

      verify(resourceActuator).checkResource(name, apiVersion, kind)
    }
  }

}
