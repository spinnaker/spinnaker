package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.spring.test.DisableSpringScheduling
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import strikt.api.expectThat
import strikt.assertions.isNotEqualTo
import strikt.assertions.startsWith
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException

@SpringBootTest(
  classes = [KeelApplication::class, ThreadCapturingEventListener::class],
  webEnvironment = MOCK
)
@DisableSpringScheduling
internal class ApplicationEventTests {

  @Autowired
  lateinit var publisher: ApplicationEventPublisher

  @Autowired
  lateinit var listener: ThreadCapturingEventListener

  @Test
  fun `events are dispatched on a different thread`() {
    val testThread = Thread.currentThread()

    publisher.publishEvent(TestEvent(this))

    val eventThread = listener.awaitInvoked(Duration.ofMillis(300))

    expectThat(eventThread)
      .isNotEqualTo(testThread)
      .get { name }
      .startsWith("event-pool-")
  }
}

internal class TestEvent(source: Any) : ApplicationEvent(source)

@Component
internal class ThreadCapturingEventListener : ApplicationListener<TestEvent> {

  private val latch = CountDownLatch(1)
  private var invokedThread: Thread? = null

  override fun onApplicationEvent(event: TestEvent) {
    invokedThread = Thread.currentThread()
    latch.countDown()
  }

  fun awaitInvoked(duration: Duration): Thread {
    if (!latch.await(duration.toMillis(), MILLISECONDS)) {
      throw TimeoutException("No value was set within $duration")
    }
    return invokedThread ?: error("Value was set but is null")
  }
}
