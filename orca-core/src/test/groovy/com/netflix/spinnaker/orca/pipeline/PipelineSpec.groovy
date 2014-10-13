/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.PipelineStatus.*

@Unroll
class PipelineSpec extends Specification {

  def stage1 = Stub(Stage)
  def stage2 = Stub(Stage)
  @Subject pipeline = new Pipeline("foo", stage1, stage2)

  def "a pipeline's status is #expectedStatus if one of its stages is #expectedStatus"() {
    given:
    stage1.getStatus() >> stage1Status
    stage2.getStatus() >> stage2Status

    expect:
    pipeline.status == expectedStatus

    where:
    stage1Status | stage2Status | expectedStatus
    NOT_STARTED  | NOT_STARTED  | NOT_STARTED
    RUNNING      | NOT_STARTED  | RUNNING
    SUCCEEDED    | NOT_STARTED  | RUNNING
    SUCCEEDED    | RUNNING      | RUNNING
    SUCCEEDED    | SUCCEEDED    | SUCCEEDED
    FAILED       | NOT_STARTED  | FAILED
    SUCCEEDED    | FAILED       | FAILED
    SUSPENDED    | NOT_STARTED  | SUSPENDED
    SUCCEEDED    | SUSPENDED    | SUSPENDED
  }

}
