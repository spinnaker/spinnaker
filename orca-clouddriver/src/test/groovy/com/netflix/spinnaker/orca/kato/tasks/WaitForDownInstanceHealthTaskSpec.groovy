/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks

import spock.lang.Specification
import spock.lang.Unroll

class WaitForDownInstanceHealthTaskSpec extends Specification {
  @Unroll
  def "should succeed when at least one health provider is 'Down'/'Out of Service' and none are 'Up'"() {
    expect:
    new WaitForDownInstanceHealthTask().hasSucceeded(healthProviders) == hasSucceeded

    where:
    healthProviders                               || hasSucceeded
    null                                          || true
    []                                            || true
    [[state: "Up"]]                               || false
    [[state: "Down"], [state: "Up"]]              || false
    [[state: "OutOfService"], [state: "Up"]]      || false
    [[state: "Down"], [state: "OutOfService"]]    || true
    [[state: "Down"]]                             || true
    [[state: "OutOfService"]]                     || true
    [[state: "OutOfService"], [state: "Unknown"]] || true
  }

}
