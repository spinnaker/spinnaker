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
package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DIFF_FINGERPRINT
import java.time.Clock
import org.jooq.DSLContext

class SqlDiffFingerprintRepository(
  private val jooq: DSLContext,
  private val clock: Clock
) : DiffFingerprintRepository {
  override fun store(resourceId: ResourceId, diff: ResourceDiff<*>) {
    val hash = diff.generateHash()
    jooq
      .select(DIFF_FINGERPRINT.COUNT, DIFF_FINGERPRINT.FIRST_DETECTION_TIME, DIFF_FINGERPRINT.HASH)
      .from(DIFF_FINGERPRINT)
      .where(DIFF_FINGERPRINT.RESOURCE_ID.eq(resourceId.toString()))
      .fetchOne()
      ?.let { (count, firstDetectionTime, existingHash) ->
        var newCount = 1
        var newTime = clock.instant().toEpochMilli()
        if (hash == existingHash) {
          newCount = count + 1
          newTime = firstDetectionTime
        }
        jooq.update(DIFF_FINGERPRINT)
          .set(DIFF_FINGERPRINT.HASH, hash)
          .set(DIFF_FINGERPRINT.COUNT, newCount)
          .set(DIFF_FINGERPRINT.FIRST_DETECTION_TIME, newTime)
          .where(DIFF_FINGERPRINT.RESOURCE_ID.eq(resourceId.toString()))
          .execute()
        return
      }

    // if there's a duplicate key here we have a bigger issue - either there's something wrong with our data,
    // or multiple instances are checking the resource at the same time
    jooq.insertInto(DIFF_FINGERPRINT)
      .set(DIFF_FINGERPRINT.RESOURCE_ID, resourceId.toString())
      .set(DIFF_FINGERPRINT.HASH, hash)
      .set(DIFF_FINGERPRINT.COUNT, 1)
      .set(DIFF_FINGERPRINT.FIRST_DETECTION_TIME, clock.instant().toEpochMilli())
      .execute()
  }

  override fun diffCount(resourceId: ResourceId): Int {
    val count = jooq
      .select(DIFF_FINGERPRINT.COUNT)
      .from(DIFF_FINGERPRINT)
      .where(DIFF_FINGERPRINT.RESOURCE_ID.eq(resourceId.toString()))
      .fetchOne()
      ?.let { (count) ->
        count
      }

    return count ?: 0
  }
}
