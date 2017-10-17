/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q

import java.time.Duration.ZERO
import java.time.temporal.TemporalAmount

interface Queue {
  /**
   * Polls the queue for ready messages.
   *
   * Implementations may invoke [callback] any number of times. Some
   * implementations may deliver a maximum of one message per call, others may
   * deliver all ready messages.
   *
   * If no messages exist on the queue or all messages have a remaining delay
   * [callback] is not invoked.
   *
   * Messages *must* be acknowledged by calling the function passed to
   * [callback] or they will be retried after [ackTimeout]. Acknowledging via a
   * nested callback allows the message to be processed asynchronously.
   *
   * @param callback invoked with the next message from the queue if there is
   * one and an _acknowledge_ function to call once processing is complete.
   */
  fun poll(callback: QueueCallback): Unit

  /**
   * Push [message] for immediate delivery.
   */
  fun push(message: Message): Unit = push(message, ZERO)

  /**
   * Push [message] for delivery after [delay].
   */
  fun push(message: Message, delay: TemporalAmount): Unit

  /**
   * Update [message] if it exists for immediate delivery.
   */
  fun reschedule(message: Message): Unit = reschedule(message, ZERO)

  /**
   * Update [mesasge] if it exists for delivery after [delay].
   */
  fun reschedule(message: Message, delay: TemporalAmount): Unit

  /**
   * Check for any un-acknowledged messages that are overdue and move them back
   * onto the queue.
   *
   * This method is not intended to be called by clients directly but typically
   * scheduled in some way.
   */
  fun retry(): Unit {}

  /**
   * The expired time after which un-acknowledged messages will be retried.
   */
  val ackTimeout: TemporalAmount

  /**
   * A handler for messages that have failed to acknowledge delivery more than
   * [Queue.ackTimeout] times.
   */
  val deadMessageHandler: DeadMessageCallback

  companion object {
    /**
     * The maximum number of times an un-acknowledged message will be retried
     * before failing permanently.
     */
    val maxRetries: Int = 5
  }
}

/**
 * The callback parameter type passed to [Queue.poll]. The queue implementation
 * will invoke the callback passing the next message from the queue and an "ack"
 * function used to acknowledge successful processing of the message.
 */
typealias QueueCallback = (Message, () -> Unit) -> Unit

typealias DeadMessageCallback = (Queue, Message) -> Unit
