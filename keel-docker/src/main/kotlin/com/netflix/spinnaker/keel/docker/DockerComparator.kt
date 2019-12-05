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
package com.netflix.spinnaker.keel.docker

import com.netflix.spinnaker.keel.docker.SortType.INCREASING
import com.netflix.spinnaker.keel.docker.SortType.SEMVER
import com.netflix.spinnaker.keel.docker.TagVersionStrategy.INCREASING_TAG
import com.netflix.spinnaker.keel.docker.TagVersionStrategy.SEMVER_TAG
import net.swiftzer.semver.SemVer

class DockerComparator {
  companion object {
    fun sort(tags: List<String>, strategy: TagVersionStrategy, customCaptureGroupRegex: String?): List<String> =
      if (customCaptureGroupRegex == null && strategy in listOf(INCREASING_TAG, SEMVER_TAG)) {
        sortRegular(tags, strategy.sortType)
      } else {
        sortRegex(tags, strategy.sortType, customCaptureGroupRegex ?: strategy.regex)
      }

    private fun sortRegular(tags: List<String>, sortType: SortType): List<String> =
      when (sortType) {
        INCREASING -> tags.sorted().reversed()
        SEMVER -> tags.filter { isSemver(it) }.sortedWith(SEMVER_COMPARATOR).reversed()
      }

    /**
     * To process the tags we create a map where:
     *  the key is result of applying the regex and extraction the capture group
     *  and the value is the original tag.
     *
     *  We process this way so that we can sort the keys while still remembering the tag value to return.
     */
    private fun sortRegex(tags: List<String>, sortType: SortType, captureGroup: String): List<String> {
      val tagMap: MutableMap<String, String> = mutableMapOf()
      tags.forEach { tag ->
        parseTag(tag, captureGroup)?.let { result ->
          tagMap.put(result, tag)
        }
      }
      return when (sortType) {
        INCREASING -> tagMap.toSortedMap().values.reversed()
        SEMVER -> tagMap.filter { isSemver(it.key) }.toSortedMap(SEMVER_COMPARATOR).values.toList().reversed()
      }
    }

    fun parseTag(tag: String, captureGroup: String): String? {
      val regex = Regex(captureGroup)
      val result = regex.find(tag)
      return if (result == null) {
        null
      } else {
        if (result.groupValues.size != 2) {
          throw InvalidRegexException(captureGroup, tag)
        }
        result.groupValues[1]
      }
    }
  }
}

fun isSemver(input: String) =
  try {
    SemVer.parse(input.removePrefix("v"))
    true
  } catch (e: IllegalArgumentException) {
    false
  }

/**
 * SemVer comparator that also trims a leading "v" off of the version, if present.
 */
val SEMVER_COMPARATOR: Comparator<String> = Comparator<String> { a, b ->
  SemVer.parse(a.removePrefix("v"))
    .compareTo(SemVer.parse(b.removePrefix("v")))
}
