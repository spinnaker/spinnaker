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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import java.nio.file.Path;
import java.util.function.Supplier;

class RequestUtils {
  static DaemonResponse.UpdateRequestBuilder getUpdateRequestBuilder(
      HalconfigParser halconfigParser) {
    DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();
    builder.setRevert(halconfigParser::undoChanges);
    builder.setSave(halconfigParser::saveConfig);
    return builder;
  }

  static void addValidation(
      DaemonResponse.UpdateRequestBuilder builder,
      ValidationSettings validationSettings,
      Supplier<ProblemSet> validator) {
    builder.setSeverity(validationSettings.getSeverity());
    if (validationSettings.isValidate()) {
      builder.setValidate(validator);
    } else {
      builder.setValidate(ProblemSet::new);
    }
  }

  static void addCleanStep(
      DaemonResponse.UpdateRequestBuilder builder,
      HalconfigParser halconfigParser,
      Path stagePath) {
    builder.setClean(() -> halconfigParser.cleanLocalFiles(stagePath));
  }
}
