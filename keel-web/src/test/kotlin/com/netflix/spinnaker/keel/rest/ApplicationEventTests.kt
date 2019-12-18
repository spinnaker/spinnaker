package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.expectThat
import strikt.assertions.isNotEqualTo
import strikt.assertions.startsWith

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, ThreadCapturingEventListener::class],
  webEnvironment = MOCK
)
internal class ApplicationEventTests {

  @Autowired
  lateinit var publisher: ApplicationEventPublisher

  @Autowired
  lateinit var listener: ThreadCapturingEventListener

  @Test
  fun `events are dispatched on a different thread`() {
    val testThread = Thread.currentThread()

    publisher.publishEvent(TestEvent(this))

    val eventThread = listener.awaitInvoked(Duration.ofMillis(100))

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
  lateinit var invokedThread: Thread

  override fun onApplicationEvent(event: TestEvent) {
    invokedThread = Thread.currentThread()
    latch.countDown()
  }

  fun awaitInvoked(duration: Duration): Thread {
    latch.await(duration.toMillis(), TimeUnit.MILLISECONDS)
    return invokedThread
  }
}
