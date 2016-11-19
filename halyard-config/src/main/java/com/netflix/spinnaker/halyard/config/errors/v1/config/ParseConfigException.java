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

package com.netflix.spinnaker.halyard.config.errors.v1.config;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;

public class ParseConfigException extends HalconfigException {
  public ParseConfigException(UnrecognizedPropertyException e) {
    Problem
        problem = new ProblemBuilder(Problem.Severity.FATAL, "Unrecognized property in your halconfig: " + e.getMessage()).build();
    getProblems().add(problem);
  }

  public ParseConfigException(ParserException e) {
    Problem problem = new ProblemBuilder(Problem.Severity.FATAL, "Could not parse your halconfig: " + e.getMessage()).build();
    getProblems().add(problem);
  }

  public ParseConfigException(ScannerException e) {
    Problem problem = new ProblemBuilder(Problem.Severity.FATAL, "Could not parse your halconfig: " + e.getMessage()).build();
    getProblems().add(problem);
  }
}
