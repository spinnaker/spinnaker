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

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;

/**
 * This is meant for requests that Halyard cannot figure out how to handle. For example: Asking to
 * load an account that isn't in your config.
 */
public class ConfigNotFoundException extends HalException {
  @Getter private int responseCode = HttpServletResponse.SC_NOT_FOUND;

  public ConfigNotFoundException(Problem problem) {
    super(problem);
  }
}
