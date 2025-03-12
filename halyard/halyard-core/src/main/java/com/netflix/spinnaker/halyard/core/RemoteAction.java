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

package com.netflix.spinnaker.halyard.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.IOException;
import java.nio.file.Path;
import lombok.Data;

@Data
public class RemoteAction {
  @JsonIgnore private String script = "";
  private String scriptPath;
  private String scriptDescription;
  private boolean autoRun;

  public void commitScript(Path path) {
    AtomicFileWriter writer = null;
    try {
      writer = new AtomicFileWriter(path);
      writer.write(script);
      writer.commit();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new HalException(
          Problem.Severity.FATAL,
          "Failed to write config for profile "
              + path.toFile().getName()
              + ": "
              + ioe.getMessage());
    } finally {
      if (writer != null) {
        writer.close();
      }
    }

    path.toFile().setExecutable(true);

    scriptPath = path.toString();
  }
}
