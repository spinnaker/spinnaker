/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.travis.client.model.v3;

import com.netflix.spinnaker.igor.build.model.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TravisBuildStateTest {
  @ParameterizedTest
  @CsvSource({"started,BUILDING", "passed,SUCCESS", "errored,FAILURE"})
  void convertTravisBuildStatesToResultObject(String travisBuildStateName, Result expectedResult) {
    TravisBuildState travisBuildState = TravisBuildState.valueOf(travisBuildStateName);
    assertEquals(expectedResult, travisBuildState.getResult());
  }

  @ParameterizedTest
  @CsvSource({"started,true", "created,true", "passed,false", "errored,false"})
  void checkIfBuildIsRunning(String travisBuildStateName, boolean expectedResult) {
    TravisBuildState travisBuildState = TravisBuildState.valueOf(travisBuildStateName);
    assertEquals(expectedResult, travisBuildState.isRunning());
  }
}
