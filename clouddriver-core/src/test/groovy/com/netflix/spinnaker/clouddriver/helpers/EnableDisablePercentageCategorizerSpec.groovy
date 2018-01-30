/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.helpers

import spock.lang.Specification
import spock.lang.Unroll


class EnableDisablePercentageCategorizerSpec extends Specification {
  @Unroll
  void "should pick the right number of elements to move between lists"() {
    when:
    def output = EnableDisablePercentageCategorizer.getInstancesToModify(modified, unmodified, percent)

    then:
    if (modified.size() + unmodified.size() == 0) {
      output.size() == 0
    } else {
      (output.size() + modified.size()) / (modified.size() + unmodified.size()) >= (float) percent / 1000
    }

    output.size() == expectedInstancesToDisableCount

    where:
    modified || unmodified || percent || expectedInstancesToDisableCount
    [1] * 0  || [1] * 4    || 100     || 4
    [1] * 0  || [1] * 4    || 25      || 1
    [1] * 0  || [1] * 7    || 10      || 1

    [1] * 4  || [1] * 0    || 100     || 0
    [1] * 3  || [1] * 0    || 10      || 0
    [1] * 9  || [1] * 0    || 60      || 0

    [1] * 0  || [1] * 0    || 0       || 0
    [1] * 0  || [1] * 0    || 100     || 0
    [1] * 0  || [1] * 0    || 40      || 0

    [1] * 9  || [1] * 9    || 60      || 2
    [1] * 7  || [1] * 3    || 10      || 0
    [1] * 4  || [1] * 4    || 100     || 4
    [1] * 5  || [1] * 10   || 0       || 0
    [1] * 9  || [1] * 10   || 90      || 9

    [1] * 0  || [1] * 3    || 33      || 1
  }
}
