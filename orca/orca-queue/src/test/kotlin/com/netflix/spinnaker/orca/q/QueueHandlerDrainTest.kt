/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.config.QueueProperties
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.q.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

class QueueHandlerDrainTest {

  private val pool = ThreadPoolTaskExecutor().apply {
    corePoolSize = 1
    maxPoolSize = 1
    setQueueCapacity(0)
    initialize()
  }

  private fun drain(waitSeconds: Long) =
    QueueHandlerDrain(
      pool,
      mock<Queue>(),
      mock<ExecutionRepository>(),
      QueueProperties().apply { shutdownWaitSeconds = waitSeconds }
    ).drain()

  @Test
  fun `waits for a running handler to finish`() {
    val started = CountDownLatch(1)
    val finished = AtomicBoolean()
    pool.execute {
      started.countDown()
      Thread.sleep(300)
      finished.set(true)
    }
    started.await()

    drain(waitSeconds = 10)

    assertTrue(finished.get())
  }

  @Test
  fun `gives up on a handler that outlasts the wait`() {
    val started = CountDownLatch(1)
    val interrupted = CountDownLatch(1)
    pool.execute {
      started.countDown()
      try {
        Thread.sleep(60_000)
      } catch (e: InterruptedException) {
        interrupted.countDown()
      }
    }
    started.await()

    drain(waitSeconds = 1)
    assertFalse(pool.threadPoolExecutor.isTerminated)

    // the pool's own destruction is what interrupts what is left
    pool.destroy()
    assertTrue(interrupted.await(5, java.util.concurrent.TimeUnit.SECONDS))
  }

  @Test
  fun `drains before the queue and repository it depends on are destroyed`() {
    val order = mutableListOf<String>()
    val queue = object : Queue by mock<Queue>(), DisposableBean {
      override fun destroy() { order.add("queue") }
    }
    val repository = mock<ExecutionRepository>(
      extraInterfaces = arrayOf(DisposableBean::class)
    ).also {
      org.mockito.kotlin.whenever((it as DisposableBean).destroy()).then { order.add("repository") }
    }
    val started = CountDownLatch(1)
    pool.execute {
      started.countDown()
      Thread.sleep(300)
      order.add("handler")
    }
    started.await()

    AnnotationConfigApplicationContext().use { context ->
      context.registerBean("messageHandlerPool", ThreadPoolTaskExecutor::class.java, java.util.function.Supplier { pool })
      context.registerBean(Queue::class.java, java.util.function.Supplier<Queue> { queue })
      context.registerBean(ExecutionRepository::class.java, java.util.function.Supplier<ExecutionRepository> { repository })
      context.registerBean(QueueProperties::class.java, *emptyArray())
      context.registerBean(QueueHandlerDrain::class.java, *emptyArray())
      context.refresh()
    }

    assertTrue(order.indexOf("handler") < order.indexOf("queue"), order.toString())
    assertTrue(order.indexOf("handler") < order.indexOf("repository"), order.toString())
  }
}
