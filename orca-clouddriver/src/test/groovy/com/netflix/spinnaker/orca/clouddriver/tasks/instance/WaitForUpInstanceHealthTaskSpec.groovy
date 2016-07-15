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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import spock.lang.Specification
import spock.lang.Unroll

class WaitForUpInstanceHealthTaskSpec extends Specification {
  @Unroll("#interestedHealthProviderNames instance: #instance => shouldBeUp: #shouldBeUp")
  def "test if instance with a given interested health provider name should be considered up"() {
    expect:
    new WaitForUpInstanceHealthTask().hasSucceeded(instance, interestedHealthProviderNames) == shouldBeUp

    where:
    interestedHealthProviderNames || instance                                                                                                            || shouldBeUp
    []                            || [health: []]                                                                                                        || false
    []                            || [health: null]                                                                                                      || false
    ["Amazon"]                    || [health: [[type: "Amazon", healthClass: "platform", state: "Unknown"]]]                                             || true
    ["Amazon", "Discovery"]       || [health: [[type: "Amazon", healthClass: "platform", state: "Unknown"], [type: "Discovery", state: "Down"]]]         || false
    ["Amazon", "Discovery"]       || [health: [[type: "Amazon", healthClass: "platform", state: "Unknown"], [type: "Discovery", state: "OutOfService"]]] || true
    ["Discovery"]                 || [health: [[type: "Discovery", state: "Down"]]]                                                                      || false
    ["Discovery"]                 || [health: [[type: "Discovery", state: "OutOfService"]]]                                                              || false
    ["Discovery", "Other"]        || [health: [[type: "Other", state: "Down"]]]                                                                          || false
    ["Amazon"]                    || [health: []]                                                                                                        || false
    ["Amazon"]                    || [health: [[type: "Amazon", healthClass: "platform", state: "Up"]]]                                                  || true
    ["Amazon", "Discovery"]       || [health: [[type: "Amazon", healthClass: "platform", state: "Unknown"], [type: "Discovery", state: "Up"]]]           || true
    ["Discovery"]                 || [health: [[type: "Discovery", state: "Up"]]]                                                                        || true
    ["Discovery"]                 || [health: [[type: "Other", state: "Up"]]]                                                                            || false
    ["Discovery", "Other"]        || [health: [[type: "Other", state: "Up"]]]                                                                            || true
    ["Discovery"]                 || [health: [[type: "Other", state: "Down"]]]                                                                          || false
  }

}
