package com.netflix.spinnaker.q.redis.spring

import com.netflix.spinnaker.config.QueueConfiguration
import com.netflix.spinnaker.config.RedisQueueConfiguration
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.redis.RedisQueue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [QueueConfiguration::class, RedisQueueConfiguration::class],
  webEnvironment = NONE
)
internal class SpringStartupTests {

  @Autowired
  lateinit var queue: Queue

  @Test
  fun `starts up successfully`() {
    Assertions.assertThat(queue).isNotNull.isInstanceOf(RedisQueue::class.java)
  }
}
