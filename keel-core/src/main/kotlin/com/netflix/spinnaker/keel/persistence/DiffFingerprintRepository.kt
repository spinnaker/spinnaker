/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.persistence

import ch.qos.logback.core.encoder.ByteArrayUtil
import com.netflix.spinnaker.keel.api.ResourceDiff
import java.security.MessageDigest

/**
 * Stores a hash of the diff and a record of the number of times we've taken action to correct it
 */
interface DiffFingerprintRepository {
  fun store(entityId: String, diff: ResourceDiff<*>)

  fun markActionTaken(entityId: String)

  fun diffCount(entityId: String): Int

  fun actionTakenCount(entityId: String): Int

  fun seen(entityId: String, diff: ResourceDiff<*>): Boolean

  fun clear(entityId: String)

  fun ResourceDiff<*>.generateHash(): String {
    val bytes = MessageDigest
      .getInstance("SHA-1")
      .digest(toDeltaJson().toString().toByteArray())
    return ByteArrayUtil.toHexString(bytes).toUpperCase()
  }
}
