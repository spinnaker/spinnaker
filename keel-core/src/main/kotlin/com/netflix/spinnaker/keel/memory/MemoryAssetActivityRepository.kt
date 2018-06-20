/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.memory

import com.netflix.spinnaker.keel.ActivityRecord
import com.netflix.spinnaker.keel.AssetActivityRepository
import com.netflix.spinnaker.keel.model.ListCriteria
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct

class MemoryAssetActivityRepository : AssetActivityRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  private val activities: ConcurrentHashMap<String, MutableList<ActivityRecord>> = ConcurrentHashMap()

  @PostConstruct
  fun init() {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun record(activity: ActivityRecord) {
    ensureAssetLog(activity.assetId)
    activities[activity.assetId]!!.add(activity)
  }

  override fun getHistory(assetId: String, criteria: ListCriteria): List<ActivityRecord> {
    ensureAssetLog(assetId)
    return activities[assetId]!!.let { limitOffset(it, criteria) }
  }

  override fun <T : ActivityRecord> getHistory(assetId: String, kind: Class<T>, criteria: ListCriteria): List<T> {
    ensureAssetLog(assetId)
    return activities[assetId]!!
      .filterIsInstance(kind)
      .let { limitOffset(it, criteria) }
  }

  private fun ensureAssetLog(assetId: String) {
    if (!activities.containsKey(assetId)) {
      activities[assetId] = mutableListOf()
    }
  }

  private fun <T : ActivityRecord> limitOffset(list: List<T>, criteria: ListCriteria): List<T> =
    list.let {
      val size = it.size
      if (size <= criteria.offset) {
        listOf()
      } else {
        var lastIndex = criteria.offset + criteria.limit
        if (lastIndex >= size) {
          lastIndex = size
        }
        it.subList(criteria.offset, lastIndex).toList()
      }
    }
}
