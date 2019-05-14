/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.kork.web.exceptions.ValidationException;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import java.util.Collections;

public interface PipelineValidator {

  /**
   * Determines if a pipeline is able to execute.
   *
   * @throws PipelineValidationFailed if the pipeline cannot run.
   */
  void checkRunnable(Execution pipeline);

  abstract class PipelineValidationFailed extends ValidationException {
    public PipelineValidationFailed(String message) {
      super(message, Collections.emptyList());
    }
  }
}
