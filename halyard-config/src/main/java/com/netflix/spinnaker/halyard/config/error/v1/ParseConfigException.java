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
 */

package com.netflix.spinnaker.halyard.config.error.v1;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;

public class ParseConfigException extends HalException {
  public ParseConfigException(UnrecognizedPropertyException e) {
    Problem problem =
        new ConfigProblemBuilder(
                Problem.Severity.FATAL,
                "Unrecognized property in your halconfig: " + e.getMessage())
            .build();
    getProblems().add(problem);
  }

  public ParseConfigException(ParserException e) {
    Problem problem =
        new ConfigProblemBuilder(
                Problem.Severity.FATAL, "Could not parse your halconfig: " + e.getMessage())
            .build();
    getProblems().add(problem);
  }

  public ParseConfigException(ScannerException e) {
    Problem problem =
        new ConfigProblemBuilder(
                Problem.Severity.FATAL, "Could not parse your halconfig: " + e.getMessage())
            .build();
    getProblems().add(problem);
  }

  public ParseConfigException(IllegalArgumentException e) {
    Problem problem =
        new ConfigProblemBuilder(
                Problem.Severity.FATAL, "Could not translate your halconfig: " + e.getMessage())
            .build();
    getProblems().add(problem);
  }
}
