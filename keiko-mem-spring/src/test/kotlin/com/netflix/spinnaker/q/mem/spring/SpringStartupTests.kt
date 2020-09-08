package com.netflix.spinnaker.q.mem.spring

import com.netflix.spinnaker.config.MemQueueConfiguration
import com.netflix.spinnaker.config.QueueConfiguration
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.memory.InMemoryQueue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [QueueConfiguration::class, MemQueueConfiguration::class, TestConfiguration::class],
  webEnvironment = SpringBootTest.WebEnvironment.NONE
)
internal class SpringStartupTests {

  @Autowired
  lateinit var queue: Queue

  @Test
  fun `starts up successfully`() {
    Assertions.assertThat(queue).isNotNull.isInstanceOf(InMemoryQueue::class.java)
  }
}

@Configuration
internal class TestConfiguration {
  @Bean
  fun deadMessageCallback(): DeadMessageCallback = { _, _ -> }
}
