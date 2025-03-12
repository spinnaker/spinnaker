/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.config.validate.v1

import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService
import spock.lang.Specification

class HalconfigValidatorSpec extends Specification {
  void "complains that you're out of date"() {
    given:
    VersionsService versionsServiceMock = Mock(VersionsService)
    versionsServiceMock.getLatestHalyardVersion() >> latest
    versionsServiceMock.getRunningHalyardVersion() >> current
    HalconfigValidator validator = new HalconfigValidator()
    validator.versionsService = versionsServiceMock

    ConfigProblemSetBuilder problemBuilder = new ConfigProblemSetBuilder(null);

    when:
    validator.validate(problemBuilder, null)

    then:
    def problems = problemBuilder.build().problems
    problems.size() == 1
    problems.get(0).message.contains("please update")

    where:
    current    | latest
    "0.0.0"    | "1.0.0"
    "0.0.0"    | "0.1.0"
    "0.0.0"    | "0.0.1"
    "1.0.0"    | "1.0.1"
    "1.1.0"    | "1.1.1"
    "1.2.0"    | "2.0.0"
    "1.1.0"    | "10.0.0"
    "3.1.0"    | "10.0.0"
    "30.1.0-1" | "100.0.0"
    "0.0.0-1"  | "1.0.0-0"
    "0.0.0-2"  | "0.1.0-2"
    "0.0.0-3"  | "0.0.1-1"
    "0.0.1-1"  | "1.0.0-0"
    "0.0.1-2"  | "0.1.0-2"
    "0.0.1-3"  | "0.1.1-1"
  }
}
