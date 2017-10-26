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
package com.netflix.spinnaker.keel.model

import spock.lang.Specification

class OrchestrationRequestSpec extends Specification {

  def 'should build job request'() {
    given:
    def req = new OrchestrationRequest("wait for nothing", "keel", "my orchestration", [
      new Job("wait", [waitTime: 30])
    ], new Trigger("1", "keel", "keel"))

    expect:
    req.name == "wait for nothing"
    req.application == "keel"
    req.description == "my orchestration"
    req.job[0].type == "wait"
    req.job[0].waitTime == 30
  }
}
