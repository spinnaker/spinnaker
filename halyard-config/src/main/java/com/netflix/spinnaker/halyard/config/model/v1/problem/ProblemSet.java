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

package com.netflix.spinnaker.halyard.config.model.v1.problem;

import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This represents all problems collected when validating the currently loaded/modified halconfig.
 */
public class ProblemSet {
  @Getter
  private List<Problem> problems = new ArrayList<>();

  /**
   * Sort the listed problems in increasing order of severity.
   */
  public void sortIncreasingSeverity() {
    Collections.sort(problems, (Problem a, Problem b) -> a.getSeverity().compareTo(b.getSeverity()));
  }

  public void add(Problem problem) {
    problems.add(problem);
  }

  public ProblemSet(List<Problem> problems) {
    this.problems = problems;
  }

  public ProblemSet() { }

  /**
   * This will throw an exception if this problem set stores any problems.
   */
  public void throwIfProblem() {
    if (!problems.isEmpty()) {
      throw new HalconfigException(problems);
    }
  }

  /**
   * Find the highest severity this problem set stores.
   *
   * @return the highest severity this problem set stores.
   */
  private Problem.Severity maxSeverity() {
    if (problems.isEmpty()) {
      return Problem.Severity.NONE;
    }

    return problems
        .stream()
        .map(Problem::getSeverity)
        .reduce(Problem.Severity.NONE, (a, b) -> a.compareTo(b) > 0 ? a : b);
  }

  /**
   * This is can be used to ignore errors that user deems frivolous.
   *
   * Example: A client's Jenkins instance isn't connecting to Halyard, but they are sure it will connect to Igor, so they
   * can force halyard to only generate an error if the severity exceeds "FATAL" (which is impossible).
   *
   * @param severity is the severity to compare all errors to that this problem set stores.
   */
  void throwifSeverityExceeds(Problem.Severity severity) {
    if (maxSeverity().compareTo(severity) > 0) {
      throw new HalconfigException(problems);
    }
  }
}
