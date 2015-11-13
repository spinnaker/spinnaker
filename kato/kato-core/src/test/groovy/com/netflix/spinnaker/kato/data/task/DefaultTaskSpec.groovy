/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kato.data.task

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DefaultTaskSpec extends Specification {

  @Subject
  DefaultTask task

  def setup() {
    task = new DefaultTask("1")
  }

  void "status updates and appends to history"() {
    given:
    task.updateStatus "TEST", "Status Update"

    expect:
    task.status.phase == "TEST"
    task.status.status == "Status Update"
    task.history*.status.contains("Status Update")
  }

  void "task state is checked on updates"() {
    setup:
    task.complete()

    when:
    task.updateStatus "TEST", "Another Status Update"

    then:
    thrown IllegalStateException
  }

  void "task state is checked on complete/fail"() {
    setup:
    task.complete()

    when:
    task.complete()

    then:
    thrown IllegalStateException

    when:
    task.fail()

    then:
    thrown IllegalStateException
  }

  void "failing a task completes it too"() {
    given:
    task.fail()

    expect:
    task.status.isCompleted()
  }

  void "history status object doesnt serialize complete and fail"() {
    setup:
    def om = new ObjectMapper()
    task.updateStatus "TEST", "Testing Serialization"

    when:
    def json = om.writeValueAsString(task.history)

    then:
    !json.contains("complete")
    !json.contains("failed")

  }
}
