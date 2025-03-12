/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.util.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import java.util.function.Supplier;
import lombok.Builder;

@Builder
public class GenericGetRequest<T> {
  private Supplier<T> getter;
  private Supplier<ProblemSet> validator;
  private String description;

  public DaemonTask<Halconfig, T> execute(ValidationSettings validationSettings) {
    DaemonResponse.StaticRequestBuilder<T> builder =
        new DaemonResponse.StaticRequestBuilder<>(getter);
    builder.setSeverity(validationSettings.getSeverity());
    if (validationSettings.isValidate()) {
      builder.setValidateResponse(validator);
    }
    return DaemonTaskHandler.submitTask(builder::build, description);
  }
}
