/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.event

/**
 * The event sourcing library event publisher.
 *
 * This library assumes that events are persisted first into a durable store and then propagated out
 * to subscribers afterwards. There is no contract on immediacy or locality of events being delivered
 * to subscribers: This is left entirely to the implementation.
 */
interface EventPublisher {
  fun publish(event: SpinnakerEvent)
}
