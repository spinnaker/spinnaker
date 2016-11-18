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

package com.netflix.spinnaker.halyard.config.model.v1;

import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This represents all problems collected when validating the currently loaded/modified halconfig.
 */
public class HalconfigProblemSet {
  @Getter
  private List<HalconfigProblem> problems = new ArrayList<>();

  /**
   * Sort the listed problems in increasing order of severity.
   */
  public void sortIncreasingSeverity() {
    Collections.sort(problems, (HalconfigProblem a, HalconfigProblem b) -> a.getSeverity().compareTo(b.getSeverity()));
  }

  public void add(HalconfigProblem problem) {
    problems.add(problem);
  }

  public HalconfigProblemSet(List<HalconfigProblem> problems) {
    this.problems = problems;
  }

  public HalconfigProblemSet() {

  }

  /**
   * This will throw an exception if this problem set stores any problems.
   */
  public void throwIfProblem() {
    if (!problems.isEmpty()) {
      throw new HalconfigException(problems);
    }
  }
}
