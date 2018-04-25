/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.qos

/**
 * The state of the QoS buffering system.
 *
 * When the QoS system is in an ACTIVE state, all new executions will become candidates for buffering. In an INACTIVE
 * state, no executions will be buffered, however draining buffered executions will still continue according to
 * the enabled Promotion Policies.
 */
enum class BufferState {
  ACTIVE,
  INACTIVE
}
