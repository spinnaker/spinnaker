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

package com.netflix.spinnaker.halyard.config.validate.v1.util;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ValidatingFileReader {
  public static String contents(ConfigProblemSetBuilder ps, String path) {
    String noAccessRemediation = "Halyard is running as user " + System.getProperty("user.name") + ". Make sure that user can read the requested file.";
    try {
      return IOUtils.toString(new FileInputStream(path));
    } catch (FileNotFoundException e) {
      ConfigProblemBuilder problemBuilder = ps.addProblem(Problem.Severity.FATAL, "Cannot find provided path: " + e.getMessage() + ".");
      if (e.getMessage().contains("denied")) {
        problemBuilder.setRemediation(noAccessRemediation);
      }
      return null;
    } catch (IOException e) {
      ConfigProblemBuilder problemBuilder = ps.addProblem(Problem.Severity.FATAL, "Failed to read path \"" + path + "\": " + e.getMessage() + ".");
      if (e.getMessage().contains("denied")) {
        problemBuilder.setRemediation(noAccessRemediation);
      }
      return null;
    }
  }
}
