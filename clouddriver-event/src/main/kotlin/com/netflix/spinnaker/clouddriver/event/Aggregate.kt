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
 * The identifiable collection of an event log.
 *
 * Aggregates are grouped by a [type] which should be unique for each domain entity, with unique
 * [id] values therein. A [version] field is used to ensure business logic is operating on the
 * latest event state; any modification to an [Aggregate] event log will increment this value.
 * When an operation is attempted on an [version] which is not head, the event framework will
 * reject the change.
 */
class Aggregate(
  val type: String,
  val id: String,
  var version: Long
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Aggregate

    if (type != other.type) return false
    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + id.hashCode()
    return result
  }
}
