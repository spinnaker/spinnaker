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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed;

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import lombok.Data;
import org.apache.commons.io.IOUtils;

@Data
public class VaultConfigMount {
  String file;
  String contents;

  public static VaultConfigMount fromLocalFile(File localFile, String desiredPath) {
    String contents;
    try {
      String unencodedContents = IOUtils.toString(new FileInputStream(localFile));
      contents = Base64.getEncoder().encodeToString(unencodedContents.getBytes());
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to read local config file: " + localFile, e);
    }

    return new VaultConfigMount().setContents(contents).setFile(desiredPath);
  }

  public static VaultConfigMount fromString(String contents, String desiredPath) {
    contents = Base64.getEncoder().encodeToString(contents.getBytes());
    return new VaultConfigMount().setContents(contents).setFile(desiredPath);
  }
}
