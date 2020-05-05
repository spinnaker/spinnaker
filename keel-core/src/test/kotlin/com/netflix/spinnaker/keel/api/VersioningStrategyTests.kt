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
package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.INCREASING_TAG
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_JOB_COMMIT_BY_SEMVER
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.core.TagComparator
import com.netflix.spinnaker.keel.exceptions.InvalidRegexException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isNull

class VersioningStrategyTests : JUnit5Minutests {

  private val incrTags = listOf("1", "2", "3", "0")
  private val semVerTags = listOf("0.0.3", "0.1.3", "0.10.3", "0.4.1")
  private val semVerTagsWithV = listOf("v0.0.3", "v0.1.3", "v0.10.3", "v0.4.1")
  private val branchJobCommitTags = listOf("master-h1.blah", "master-h2.blah", "master-h3.blah", "master-h0.blah")
  private val semverJobCommitTags = listOf("v1.12.3-rc.1-h1192.f876e5a", "v1.12.2-h1188.35b8b29", "v1.12.2-h1182.8a5b962")
  private val trickyOtherTags = listOf("3master-h1.blah", "1master-h2.blah", "2master-h3.blah", "5master-h0.blah")

  fun tests() = rootContext<Unit> {
    context("increasing tags") {
      test("comparing gets highest") {
        val sorted = incrTags.sortedWith(TagComparator(INCREASING_TAG))
        expectThat(sorted.first()).isEqualTo("3")
      }
    }

    context("semver tags") {
      test("leading v stripped") {
        val sorted = semVerTagsWithV.sortedWith(TagComparator(SEMVER_TAG))
        expectThat(sorted.first()).isEqualTo("v0.10.3")
      }

      test("plain numbers are ok") {
        val sorted = semVerTags.sortedWith(TagComparator(SEMVER_TAG))
        expectThat(sorted.first()).isEqualTo("0.10.3")
      }
    }

    context("tags with regex parsing needed") {
      test("finds highest by branchJobCommit by job") {
        val sorted = branchJobCommitTags.sortedWith(TagComparator(BRANCH_JOB_COMMIT_BY_JOB))
        expectThat(sorted.first()).isEqualTo("master-h3.blah")
      }

      test("finds highest with confusing number in front") {
        val regex = """^\dmaster-h(\d+).*$"""
        val sorted = trickyOtherTags.sortedWith(TagComparator(INCREASING_TAG, regex))
        expectThat(sorted.first()).isEqualTo("2master-h3.blah")
      }

      test("ignores a bad tag increasing strategy") {
        val regex = """^\dmaster-h(\d+).*$"""
        val mixedTags = listOf("3master-h1.blah", "1master-h2.blah", "2master-h3.blah", "5master-h0.blah", "latest")
        val sorted = mixedTags.sortedWith(TagComparator(INCREASING_TAG, regex))
        expectThat(sorted.first()).isEqualTo("2master-h3.blah")
      }

      test("ignores a bad tag semver strategy") {
        val mixedTags = listOf("0.0.3", "0.1.3", "0.10.3", "latest", "0.4.1")
        val sorted = mixedTags.sortedWith(TagComparator(SEMVER_TAG))
        expectThat(sorted.first()).isEqualTo("0.10.3")
      }

      test("finds highest with semverJobCommit by job") {
        val sorted = semverJobCommitTags.sortedWith(TagComparator(SEMVER_JOB_COMMIT_BY_JOB, null))
        expectThat(sorted.first()).isEqualTo("v1.12.3-rc.1-h1192.f876e5a")
      }

      test("finds highest with semverJobCommit by semver") {
        val sorted = semverJobCommitTags.sortedWith(TagComparator(SEMVER_JOB_COMMIT_BY_SEMVER, null))
        expectThat(sorted.first()).isEqualTo("v1.12.3-rc.1-h1192.f876e5a")
      }
    }

    context("regex parsing") {
      test("able to parse with capture group") {
        val tag = "master-h1.blah"
        val regex = """^master-h(\d+).*$"""
        val result = TagComparator.parseWithRegex(tag, INCREASING_TAG, regex)
        expectThat(result).isEqualTo("1")
      }

      test("too many captures throws exception") {
        val tag = "master-h1.blah"
        val regex = """^master-h(\d+)(.*)$"""
        expectCatching {
          TagComparator.parseWithRegex(tag, INCREASING_TAG, regex)
        }.isFailure()
          .isA<InvalidRegexException>()
      }

      test("no match returns null") {
        val tag = "v001"
        val regex = """^master-h(\d+).*$"""
        val result = TagComparator.parseWithRegex(tag, INCREASING_TAG, regex)
        expectThat(result).isNull()
      }
    }
  }
}
