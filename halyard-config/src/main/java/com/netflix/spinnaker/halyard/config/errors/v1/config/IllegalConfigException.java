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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigCoordinates;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigProblem;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigProblemSet;

import java.util.List;

/**
 * This is reserved for Halyard configs that fall between unparseable (not valid yaml), and incorrectly configured
 * (provider-specific error). Essentially, when a config has problems that prevent halyard from validating it, although
 * it is readable by our yaml parser into the hafconfig Object, this is thrown
 */
public class IllegalConfigException extends HalconfigException {
  public IllegalConfigException(List<HalconfigProblem> problems) {
    super(problems);
  }

  public IllegalConfigException(String message, String remediation) {
    HalconfigProblem problem = new HalconfigProblem(HalconfigProblem.Severity.FATAL, message, remediation);
    getProblems().add(problem);
  }

  public IllegalConfigException(HalconfigCoordinates coordinates, String message, String remediation) {
    HalconfigProblem problem = new HalconfigProblem(HalconfigProblem.Severity.FATAL, coordinates, message, remediation);
    getProblems().add(problem);
  }
}
