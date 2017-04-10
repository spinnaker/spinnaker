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
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.core.AtomicFileWriter;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class Profile {
  @JsonIgnore
  private String contents = "";
  private List<String> requiredFiles = new ArrayList<>();
  private Map<String, String> env = new HashMap<>();

  // Name of the profile itself (not the service or artifact)
  final private String name;
  final private String version;
  // This is any data needed by the profile to generate the rest of its contents
  @JsonIgnore
  final private String baseContents;
  // Where does Spinnaker expect to find this profile
  final private String outputFile;
  boolean executable = false;

  public String getStagedFile(String stagingPath) {
    return Paths.get(stagingPath, name).toString();
  }

  public void writeStagedFile(String stagingPath) {
    AtomicFileWriter writer = null;
    Path path = Paths.get(stagingPath, name);
    log.info("Writing profile to " + path.toString() + " with " + requiredFiles.size() + " required files");
    try {
      writer = new AtomicFileWriter(path);
      writer.write(contents);
      writer.commit();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new HalException(Problem.Severity.FATAL,
              "Failed to write config for profile " + path.toFile().getName() + ": " + ioe.getMessage()
      );
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  public Profile(String name, String version, String outputFile, String baseContents) {
    this.name = name;
    this.version = version;
    this.outputFile = outputFile;
    this.baseContents = baseContents;
  }

  public Profile preppendContents(String contents) {
    this.contents = contents + "\n" + this.contents;
    return this;
  }

  public Profile appendContents(String contents) {
    this.contents += "\n" + contents;
    return this;
  }
}
