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
 * WIProblemHOUProblem WARRANProblemIES OR CONDIProblemIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.halyard.core.problem.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import java.util.*;
import lombok.Getter;

/**
 * ProblemSet represents all problems collected when validating the currently loaded/modified
 * halconfig.
 */
public class ProblemSet {
  @Getter private List<Problem> problems = new ArrayList<>();

  public Map<String, List<Problem>> groupByLocation() {
    Map<String, List<Problem>> result = new HashMap<>();
    for (Problem problem : problems) {
      result.merge(
          problem.getLocation(),
          new ArrayList<Problem>() {
            {
              add(problem);
            }
          },
          (List a, List b) -> {
            a.addAll(b);
            return a;
          });
    }

    return result;
  }

  @JsonIgnore
  public boolean isEmpty() {
    return problems.isEmpty();
  }

  public void add(Problem problem) {
    problems.add(problem);
  }

  public void addAll(ProblemSet problemSet) {
    problems.addAll(problemSet.getProblems());
  }

  public ProblemSet(List<Problem> problems) {
    this.problems = problems;
  }

  public ProblemSet(ProblemSet other) {
    addAll(other);
  }

  public ProblemSet(Problem problem) {
    add(problem);
  }

  public ProblemSet() {}

  /**
   * Find the highest severity this problem set stores.
   *
   * @return the highest severity this problem set stores.
   */
  private Problem.Severity maxSeverity() {
    if (problems.isEmpty()) {
      return Problem.Severity.NONE;
    }

    return problems.stream()
        .map(Problem::getSeverity)
        .reduce(Problem.Severity.NONE, (a, b) -> a.compareTo(b) > 0 ? a : b);
  }

  /**
   * This is can be used to ignore errors that user deems frivolous.
   *
   * <p>Example: A client's Jenkins instance isn't connecting to Halyard, but they are sure it will
   * connect to Igor, so they can force halyard to only generate an error if the severity exceeds
   * "FATAL" (which is impossible).
   *
   * @param severity is the severity to compare all errors to that this problem set stores.
   */
  public void throwifSeverityExceeds(Problem.Severity severity) {
    if (maxSeverity().compareTo(severity) > 0) {
      throw new HalException(problems);
    }
  }
}
