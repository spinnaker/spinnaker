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


class EnableDisablePercentageCategorizerSpec extends Specification {
  void "should pick the right number of elements to move between lists"() {
    when:
    def output = EnableDisablePercentageCategorizer.getInstancesToModify(modified, unmodified, percent)

    then:
    if (modified.size() + unmodified.size() == 0) {
      output.size() == 0
    } else {
      (output.size() + modified.size()) / (modified.size() + unmodified.size()) >= (float) percent / 1000
    }

    where:
    modified || unmodified || percent
    [1] * 0  || [1] * 4    || 100
    [1] * 0  || [1] * 4    || 25
    [1] * 0  || [1] * 7    || 10

    [1] * 4  || [1] * 0    || 100
    [1] * 3  || [1] * 0    || 10
    [1] * 9  || [1] * 0    || 60

    [1] * 0  || [1] * 0    || 0
    [1] * 0  || [1] * 0    || 100
    [1] * 0  || [1] * 0    || 40

    [1] * 9  || [1] * 9    || 60
    [1] * 7  || [1] * 3    || 10
    [1] * 4  || [1] * 4    || 100
    [1] * 5  || [1] * 10   || 0
    [1] * 9  || [1] * 10   || 90
}
}
