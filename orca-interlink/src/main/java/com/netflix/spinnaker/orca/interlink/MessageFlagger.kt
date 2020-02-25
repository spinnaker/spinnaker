/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.interlink

import com.google.common.base.Preconditions
import com.google.common.collect.EvictingQueue
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.netflix.spinnaker.config.InterlinkConfigurationProperties.FlaggerProperties
import com.netflix.spinnaker.orca.interlink.events.InterlinkEvent
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * To avoid pathological cases where 2 orca clusters end up endlessly bouncing the same message back and forth across the
 * interlink, MessageFlagger can act as a circuit breaker. Based on a fixed size evicting queue, it inspects the
 * fingerprint of the last ${maxSize} events published by this instance, and if it detects that we are over some
 * threshold, it will not let the message get published on the topic. One example where this may happen is the following
 * scenario with an orphaned execution that everybody considers as belonging to someone else:
 *
 * 1. execution repo 1 has an execution with id I and partition `apple`
 * 2. execution repo 2 has an execution with the same id I and partition `banana`
 * 3. when a user makes a request to orca 1 with partition `banana` to delete I, it is considered a foreign execution
 *    and a message is sent on the interlink
 * 4. orca 2 receives the interlink message and tries to delete it from execution repo 2, but it is also considered
 *    foreign (because it has partition `apple`)
 * 5. therefore, orca 2 sends a message on the interlink, and so on
 *
 * @param maxSize the maximum number of fingerprints to consider
 * @param threshold a message with fingerprint F will be flagged if the number of times F is present in the queue is
 *    over the threshold
 * @param lookback fingerprints older than the lookback duration will not count toward the threshold
 */
class MessageFlagger(val clock: Clock, val props: FlaggerProperties) {
  private val queue: EvictingQueue<TimestampedHash> = EvictingQueue.create(props.maxSize)
  private val hasher = Hashing.goodFastHash(64)
  private val lookback = Duration.ofSeconds(props.lookbackSeconds)

  init {
    Preconditions.checkArgument(props.maxSize >= props.threshold, "maxSize (%s) has to be larger than threshold (%s)", props.maxSize, props.threshold)
    Preconditions.checkArgument(props.threshold > 0, "threshold (%s) has to be > 0", props.threshold)
  }

  fun process(event: InterlinkEvent) {
    if (!props.enabled) {
      return
    }

    val hash = hasher.hashString(event.getFingerprint(), Charsets.UTF_8)
    val now = clock.instant()
    val timeCutoff = now.minus(lookback)
    val matches = queue.filter { it.timestamp.isAfter(timeCutoff) && it.hash == hash }.toList()
    if (matches.count() >= props.threshold) {
      throw MessageFlaggedException("Event '$event' with fingerprint '${event.fingerprint}' has been encountered " +
        "${matches.count()} times in the last ${props.lookbackSeconds}s")
    }

    queue.add(TimestampedHash(now, hash))
  }

  private data class TimestampedHash(val timestamp: Instant, val hash: HashCode)
}
