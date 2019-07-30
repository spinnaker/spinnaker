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

package com.netflix.spinnaker.halyard.config.model.v1.util;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ValidatingFileReader {

  private static final String NO_ACCESS_REMEDIATION =
      "Halyard is running as user "
          + System.getProperty("user.name")
          + ". Make sure that user can read the requested file.";

  public static String contents(
      ConfigProblemSetBuilder ps, String path, SecretSessionManager secretSessionManager) {

    byte[] contentBytes = contentBytes(ps, path, secretSessionManager);
    if (contentBytes == null) {
      return null;
    }
    return new String(contentBytes);
  }

  public static byte[] contentBytes(
      ConfigProblemSetBuilder ps, String path, SecretSessionManager secretSessionManager) {

    if (PropertyUtils.isConfigServerResource(path)) {
      return null;
    }

    if (EncryptedSecret.isEncryptedSecret(path)) {
      return secretSessionManager.decryptAsBytes(path);
    }

    return readFromLocalFilesystem(ps, path);
  }

  private static byte[] readFromLocalFilesystem(ConfigProblemSetBuilder ps, String path) {
    try {
      return IOUtils.toByteArray(new FileInputStream(path));
    } catch (FileNotFoundException e) {
      buildProblem(ps, "Cannot find provided path: " + e.getMessage() + ".", e);
    } catch (IOException e) {
      buildProblem(ps, "Failed to read path \"" + path + "\".", e);
    }

    return null;
  }

  private static void buildProblem(
      ConfigProblemSetBuilder ps, String message, Exception exception) {
    ConfigProblemBuilder problemBuilder =
        ps.addProblem(Problem.Severity.FATAL, message + ": " + exception.getMessage() + ".");

    if (exception.getMessage().contains("denied")) {
      problemBuilder.setRemediation(ValidatingFileReader.NO_ACCESS_REMEDIATION);
    }
  }
}
