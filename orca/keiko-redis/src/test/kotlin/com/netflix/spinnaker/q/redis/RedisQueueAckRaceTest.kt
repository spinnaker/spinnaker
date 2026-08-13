/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.q.redis

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.TestMessage
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.QueueEvent
import java.time.Clock
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

/**
 * Reproduces the message loss described in `orca-message-loss-ack-race-2026-08.md`.
 *
 * [RedisQueue.ackMessage] decides whether to delete a message outright by asking "is an identical
 * fingerprint back on the queue right now?" and then, if not, deleting every trace of that
 * fingerprint. The question and the deletion are separate round-trips, and the deletion re-derives
 * nothing — so a message pushed by another pod in between is destroyed by a cleanup routine that
 * never looked at it.
 *
 * That is not a far-fetched interleaving. A task returning `REDIRECT` is the one `RunTask` outcome
 * where the handler does *not* re-push its own fingerprint, so its ack takes the destructive
 * branch; and the redirect cascade it kicks off (reset loop tasks -> restart the loop-start task ->
 * that task completes -> start the loop-end task -> push `RunTask`) regenerates a message with
 * byte-identical content, and therefore the identical fingerprint, a few hundred milliseconds
 * later. Production execution `01KYVFKT30ZGAPQEMT98NKFTMN` lost exactly that message on
 * 2026-07-31 and sat `RUNNING` with nothing in the queue for 6h20m.
 *
 * The two pods here mirror that incident: [podA] polls and acks (pod `6lxzz`, the `REDIRECT` ack),
 * [podB] pushes the identical successor (pod `k4vp4`, `StartTaskHandler`'s `push(RunTask)`).
 * [TestMessage] is a data class, so two instances with the same payload collide on fingerprint for
 * free.
 *
 * Requires Docker: [EmbeddedRedis] is a Testcontainer. The module's `test` task is disabled
 * repo-wide, so run this via its dedicated task:
 *
 * ```
 * ./gradlew :orca:keiko-redis:redisAckRaceTest
 * ```
 */
class RedisQueueAckRaceTest {

  /**
   * Runs a one-shot action immediately after the first Redis command issued while armed.
   *
   * Deliberately not tied to any single command. Anchoring the injection to `ZRANK` alone would
   * make this test pass vacuously once the check-then-act pair is replaced by an atomic script,
   * because the hook would simply never fire — green for the wrong reason. Firing after whichever
   * command comes first keeps the injection inside the ack either way.
   */
  private class CommandHook {
    private var pending: (() -> Unit)? = null

    fun armOnce(action: () -> Unit) {
      pending = action
    }

    /** Runs the armed action exactly once, if it has not already run. */
    fun fire() {
      // Disarm before invoking: the action itself issues Redis commands.
      val action = pending ?: return
      pending = null
      action.invoke()
    }
  }

  /** A [Jedis] that lets [CommandHook] observe the boundary between consecutive commands. */
  private class HookedJedis(
    host: String,
    port: Int,
    private val hook: CommandHook
  ) : Jedis(host, port) {

    override fun zrank(key: String, member: String): Long? =
      super.zrank(key, member).also { hook.fire() }

    override fun evalsha(
      sha1: String,
      keys: MutableList<String>,
      args: MutableList<String>
    ): Any? = super.evalsha(sha1, keys, args).also { hook.fire() }
  }

  private class HookedJedisPool(
    private val host: String,
    private val port: Int,
    private val hook: CommandHook
  ) : JedisPool(host, port) {
    override fun getResource(): Jedis = HookedJedis(host, port, hook)
  }

  private val hook = CommandHook()

  private val deadMessageHandler: DeadMessageCallback = { _, _ -> }

  /** Polls and acks — the pod whose task returned `REDIRECT`. */
  private lateinit var podA: RedisQueue

  /** Pushes the identical successor — the pod running the redirect cascade. */
  private lateinit var podB: RedisQueue

  @BeforeEach
  fun setUp() {
    redis!!.jedis.use { it.flushDB() }
    // Same queue name on both: two pods sharing one set of Redis keys.
    podA = queue(HookedJedisPool(redis!!.host, redis!!.port, hook))
    podB = queue(redis!!.pool)
  }

  @Test
  fun `ack of a finished message must not delete an identical message pushed during the ack`() {
    val original = TestMessage(PAYLOAD)

    // podB enqueues the message; podA picks it up, moving the fingerprint queue -> unacked.
    podB.push(original)
    var capturedAck: (() -> Unit)? = null
    podA.poll { _, ack -> capturedAck = ack }
    val ack = requireNotNull(capturedAck) { "podA should have been handed the message" }

    // The task returned REDIRECT, so podA's handler pushes no replacement of its own and its ack
    // will take the destructive branch. Meanwhile podB's redirect cascade regenerates a
    // byte-identical message. Land it inside the ack, right after the ack's first command.
    hook.armOnce { podB.push(TestMessage(PAYLOAD)) }
    ack.invoke()

    val delivered = mutableListOf<Message>()
    podA.poll { message, _ -> delivered.add(message) }

    assertThat(delivered)
      .describedAs("the message pushed during the ack must still be deliverable")
      .containsExactly(original)
  }

  @Test
  fun `ack keeps a message the handler re-pushed before acking`() {
    // The healthy long-poll path: a task still RUNNING re-pushes its own fingerprint before
    // returning, so the ack must clear the in-flight marker without touching the queued copy.
    val original = TestMessage(PAYLOAD)

    podB.push(original)
    var capturedAck: (() -> Unit)? = null
    podA.poll { _, ack -> capturedAck = ack }
    val ack = requireNotNull(capturedAck) { "podA should have been handed the message" }

    podA.push(TestMessage(PAYLOAD))
    ack.invoke()

    val delivered = mutableListOf<Message>()
    podA.poll { message, _ -> delivered.add(message) }

    assertThat(delivered)
      .describedAs("a re-pushed message must survive the ack of the delivery it replaced")
      .containsExactly(original)
  }

  @Test
  fun `ack of a finished message cleans up completely`() {
    // Guards the other direction: the fix must not keep messages alive that are genuinely done,
    // or every completed message leaks its payload into the messages hash forever.
    podB.push(TestMessage(PAYLOAD))
    var capturedAck: (() -> Unit)? = null
    podA.poll { _, ack -> capturedAck = ack }
    val ack = requireNotNull(capturedAck) { "podA should have been handed the message" }

    ack.invoke()

    val state = podA.readState()
    assertThat(state.depth).describedAs("queued").isZero()
    assertThat(state.unacked).describedAs("unacked").isZero()
    assertThat(state.orphaned).describedAs("orphaned payloads").isZero()
  }

  private fun queue(pool: JedisPool) = RedisQueue(
    queueName = QUEUE_NAME,
    pool = pool,
    clock = Clock.systemDefaultZone(),
    mapper = ObjectMapper().apply {
      registerModule(KotlinModule.Builder().build())
      disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      registerSubtypes(TestMessage::class.java)
      registerSubtypes(AttemptsAttribute::class.java)
    },
    serializationMigrator = Optional.empty(),
    deadMessageHandlers = listOf(deadMessageHandler),
    publisher = object : EventPublisher {
      override fun publishEvent(event: QueueEvent) {}
    }
  )

  companion object {
    private const val QUEUE_NAME = "test"
    private const val PAYLOAD = "RunTask(stageId=01KYVFKT319X0WD21C756ZG53N, taskId=3)"

    private var redis: EmbeddedRedis? = null

    @BeforeAll
    @JvmStatic
    fun startRedis() {
      redis = EmbeddedRedis.embed()
    }

    @AfterAll
    @JvmStatic
    fun stopRedis() {
      redis?.destroy()
      redis = null
    }
  }
}
