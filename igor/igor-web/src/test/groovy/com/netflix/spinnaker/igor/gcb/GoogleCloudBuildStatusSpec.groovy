/*
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
 */

package com.netflix.spinnaker.igor.gcb;

import spock.lang.Specification
import spock.lang.Unroll;

class GoogleCloudBuildStatusSpec extends Specification {
  @Unroll
  def "statuses are properly ordered"() {
    when:
    def oldStatus = GoogleCloudBuildStatus.valueOf(oldStatusString)
    def newStatus = GoogleCloudBuildStatus.valueOf(newStatusString)
    def result = newStatus.greaterThanOrEqualTo(oldStatus)

    then:
    result == expected

    where:
    oldStatusString  | newStatusString  | expected
    "QUEUED"         | "WORKING"        | true
    "WORKING"        | "QUEUED"         | false
    "WORKING"        | "SUCCESS"        | true
    "SUCCESS"        | "WORKING"        | false
    "WORKING"        | "STATUS_UNKNOWN" | false
    "STATUS_UNKNOWN" | "WORKING"        | true
    "QUEUED"         | "SUCCESS"        | true
    "SUCCESS"        | "QUEUED"         | false
    "QUEUED"         | "FAILURE"        | true
    "FAILURE"        | "QUEUED"         | false
    "SUCCESS"        | "FAILURE"        | true
    "FAILURE"        | "SUCCESS"        | true
  }
}
