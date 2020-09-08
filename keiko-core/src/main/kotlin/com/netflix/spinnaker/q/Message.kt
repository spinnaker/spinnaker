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

package com.netflix.spinnaker.q

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME
import com.fasterxml.jackson.annotation.JsonTypeName

const val JSON_NAME_PROPERTY = "kind"

/**
 * Implemented by all messages used with the [Queue]. Sub-types should be simple
 * immutable value types such as Kotlin data classes or _Lombok_ `@Value`
 * classes.
 */
@JsonTypeInfo(use = NAME, property = JSON_NAME_PROPERTY) abstract class Message {
  // TODO: this type should be immutable
  val attributes: MutableList<Attribute> = mutableListOf()

  /**
   * Defines an ack timeout override in milliseconds, a null value will use the
   * queue default.
   */
  open val ackTimeoutMs: Long? = null

  /**
   * @return the attribute of type [A] or `null`.
   */
  inline fun <reified A : Attribute> getAttribute() =
    attributes.find { it is A } as A?

  /**
   * Adds an attribute of type [A] to the message.
   */
  inline fun <reified A : Attribute> setAttribute(attribute: A): A {
    attributes.removeIf { it is A }
    attributes.add(attribute)

    return attribute
  }
}

/**
 * The base type for message metadata attributes.
 */
@JsonTypeInfo(use = NAME, property = JSON_NAME_PROPERTY)
interface Attribute

@Deprecated(
  "Message attributes are intended for internal keiko use only, handlers should " +
    "limit attempts or run-time through other means."
)
/**
 * An attribute representing the maximum number of retries for a message.
 *
 * @deprecated This attribute originally combined the number of times a message
 * could be retried due to not being acked within [AckTimeoutSeconds] as well as
 * the number of times a properly acked retryable message could be requeued which
 * is normal behavior for many Orca message types.
 */
@JsonTypeName("maxAttempts")
data class MaxAttemptsAttribute(val maxAttempts: Int = -1) : Attribute

/**
 * An attribute representing the number of times a message has been retried.
 */
@JsonTypeName("attempts")
data class AttemptsAttribute(var attempts: Int = 0) : Attribute

/**
 * An attribute representing the number of times a message has been retried
 * due to ack timeouts.
 */
@JsonTypeName("ackAttempts")
data class AckAttemptsAttribute(var ackAttempts: Int = 0) : Attribute
