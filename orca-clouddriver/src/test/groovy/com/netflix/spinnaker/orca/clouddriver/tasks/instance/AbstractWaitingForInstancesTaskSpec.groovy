/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import spock.lang.Specification
import spock.lang.Unroll

class AbstractWaitingForInstancesTaskSpec extends Specification {
  @Unroll
  void "should correctly calculate #desiredPercentage% of #desired instances to enable/disable"() {
    given:
    def capacity = [min: min, max: max, desired: desired]
    def result = AbstractWaitingForInstancesTask.getDesiredInstanceCount(capacity, desiredPercentage)

    expect:
    // this is fewest enabled/disabled instances remaining after an autoscaling event
    (desired * (desiredPercentage / 100D)) - (desired - min) >= result

    where:
    min  | max  | desired | desiredPercentage
    20   | 30   | 24      | 50
    200  | 300  | 300     | 10
    10   | 300  | 24      | 50
    0    | 30   | 30      | 50
    2000 | 3000 | 2400    | 50
    2000 | 3000 | 2400    | 10
    2000 | 3000 | 2400    | 20
    2000 | 3000 | 2400    | 30
    2000 | 3000 | 2400    | 80
    2000 | 3000 | 2400    | 100
    2000 | 3000 | 2000    | 50
    2000 | 3000 | 2000    | 10
    2000 | 3000 | 2000    | 20
    2000 | 3000 | 2000    | 30
    2000 | 3000 | 2000    | 80
    2000 | 3000 | 2000    | 100
    2000 | 3000 | 3000    | 10
    2000 | 3000 | 3000    | 20
    2000 | 3000 | 3000    | 30
    2000 | 3000 | 3000    | 100
  }
}
