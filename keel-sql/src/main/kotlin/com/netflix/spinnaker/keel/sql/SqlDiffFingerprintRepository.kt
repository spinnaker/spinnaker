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

import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DIFF_FINGERPRINT
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import java.time.Clock
import org.jooq.DSLContext
import org.jooq.impl.DSL.selectFrom

class SqlDiffFingerprintRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val sqlRetry: SqlRetry
) : DiffFingerprintRepository {
  override fun store(entityId: String, diff: ResourceDiff<*>) {
    val hash = diff.generateHash()
    val record = sqlRetry.withRetry(READ) {
      jooq
        .select(DIFF_FINGERPRINT.COUNT, DIFF_FINGERPRINT.FIRST_DETECTION_TIME, DIFF_FINGERPRINT.HASH, DIFF_FINGERPRINT.COUNT_ACTIONS_TAKEN)
        .from(DIFF_FINGERPRINT)
        .where(DIFF_FINGERPRINT.ENTITY_ID.eq(entityId))
        .fetchOne()
    }
    record?.let { (count, firstDetectionTime, existingHash, countActionsTaken) ->
      var newCount = 1
      var newTime = clock.timestamp()
      var newCountActionsTaken = 0
      if (hash == existingHash) {
        newCount = count + 1
        newTime = firstDetectionTime
        newCountActionsTaken = countActionsTaken
      }
      sqlRetry.withRetry(WRITE) {
        jooq.update(DIFF_FINGERPRINT)
          .set(DIFF_FINGERPRINT.HASH, hash)
          .set(DIFF_FINGERPRINT.COUNT, newCount)
          .set(DIFF_FINGERPRINT.COUNT_ACTIONS_TAKEN, newCountActionsTaken)
          .set(DIFF_FINGERPRINT.FIRST_DETECTION_TIME, newTime)
          .where(DIFF_FINGERPRINT.ENTITY_ID.eq(entityId))
          .execute()
      }
      return
    }

    // if there's a duplicate key here we have a bigger issue - either there's something wrong with our data,
    // or multiple instances are checking the resource at the same time
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(DIFF_FINGERPRINT)
        .set(DIFF_FINGERPRINT.ENTITY_ID, entityId)
        .set(DIFF_FINGERPRINT.HASH, hash)
        .set(DIFF_FINGERPRINT.COUNT, 1)
        .set(DIFF_FINGERPRINT.COUNT_ACTIONS_TAKEN, 0)
        .set(DIFF_FINGERPRINT.FIRST_DETECTION_TIME, clock.timestamp())
        .execute()
    }
  }

  override fun markActionTaken(entityId: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.update(DIFF_FINGERPRINT)
        .set(DIFF_FINGERPRINT.COUNT_ACTIONS_TAKEN, DIFF_FINGERPRINT.COUNT_ACTIONS_TAKEN.plus(1))
        .where(DIFF_FINGERPRINT.ENTITY_ID.eq(entityId))
        .execute()
    }
  }

  override fun diffCount(entityId: String): Int {
    val count = sqlRetry.withRetry(READ) {
      jooq
        .select(DIFF_FINGERPRINT.COUNT)
        .from(DIFF_FINGERPRINT)
        .where(DIFF_FINGERPRINT.ENTITY_ID.eq(entityId))
        .fetchOne(DIFF_FINGERPRINT.COUNT)
    }
    return count ?: 0
  }

  override fun actionTakenCount(entityId: String): Int {
    val actionCount = sqlRetry.withRetry(READ) {
      jooq
        .select(DIFF_FINGERPRINT.COUNT_ACTIONS_TAKEN)
        .from(DIFF_FINGERPRINT)
        .where(DIFF_FINGERPRINT.ENTITY_ID.eq(entityId))
        .fetchOne(DIFF_FINGERPRINT.COUNT_ACTIONS_TAKEN)
    }
    return actionCount ?: 0
  }

  override fun seen(entityId: String, diff: ResourceDiff<*>): Boolean =
    sqlRetry.withRetry(READ) {
      jooq.fetchExists(
        selectFrom(DIFF_FINGERPRINT)
          .where(DIFF_FINGERPRINT.ENTITY_ID.eq(entityId))
          .and(DIFF_FINGERPRINT.HASH.eq(diff.generateHash()))
      )
    }

  override fun clear(entityId: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(DIFF_FINGERPRINT)
        .where(DIFF_FINGERPRINT.ENTITY_ID.eq(entityId))
        .execute()
    }
  }
}
