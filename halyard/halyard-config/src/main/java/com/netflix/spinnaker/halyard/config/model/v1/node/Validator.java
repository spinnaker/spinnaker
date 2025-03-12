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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.FileService;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class Validator<T extends Node> {

  private static final String NO_ACCESS_REMEDIATION =
      "Halyard is running as user "
          + System.getProperty("user.name")
          + ". Make sure that user can read the requested file.";

  @Autowired protected SecretSessionManager secretSessionManager;
  @Autowired private FileService fileService;

  public abstract void validate(ConfigProblemSetBuilder p, T n);

  protected String validatingFileDecrypt(ConfigProblemSetBuilder p, String filePath) {
    byte[] contentBytes = validatingFileDecryptBytes(p, filePath);
    if (contentBytes != null) {
      return new String(contentBytes);
    }
    return null;
  }

  protected byte[] validatingFileDecryptBytes(ConfigProblemSetBuilder p, String filePath) {
    try {
      return fileService.getFileContentBytes(filePath);
    } catch (FileNotFoundException e) {
      buildProblem(p, "Cannot find provided path: " + e.getMessage() + ".", e);
    } catch (IOException e) {
      buildProblem(p, "Failed to read path \"" + filePath + "\".", e);
    }
    return null;
  }

  protected Path validatingFileDecryptPath(String filePath) {
    return fileService.getLocalFilePath(filePath);
  }

  private void buildProblem(ConfigProblemSetBuilder ps, String message, Exception exception) {
    ConfigProblemBuilder problemBuilder =
        ps.addProblem(Problem.Severity.FATAL, message + ": " + exception.getMessage() + ".");

    if (exception.getMessage().contains("denied")) {
      problemBuilder.setRemediation(NO_ACCESS_REMEDIATION);
    }
  }
}
