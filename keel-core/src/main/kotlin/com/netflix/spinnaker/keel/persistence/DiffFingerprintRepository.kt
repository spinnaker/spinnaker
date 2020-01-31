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

import com.netflix.spinnaker.keel.diff.ResourceDiff
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

/**
 * Stores a hash of the diff
 */
interface DiffFingerprintRepository {
  fun store(resourceId: String, diff: ResourceDiff<*>)

  fun diffCount(resourceId: String): Int

  fun ResourceDiff<*>.generateHash(): String {
    val bytes = MessageDigest
      .getInstance("SHA-1")
      .digest(this.toDeltaJson().toString().toByteArray())
    return DatatypeConverter.printHexBinary(bytes).toUpperCase()
  }
}
