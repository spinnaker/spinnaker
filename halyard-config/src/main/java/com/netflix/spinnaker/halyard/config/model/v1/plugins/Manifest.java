/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.plugins;

import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.Data;
import lombok.Getter;

@Data
public class Manifest {
  public String name;
  public String manifestVersion;
  public List<String> jars;
  public Map<String, Object> options;

  static final String regex = "^[a-zA-Z0-9]+\\/[\\w-]+$";
  static final Pattern pattern = Pattern.compile(regex);

  public void validate() throws HalException {

    if (Stream.of(name, manifestVersion, jars).anyMatch(Objects::isNull)) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL, "Invalid plugin manifest, contains null values")
              .build());
    }

    Matcher matcher = pattern.matcher(name);

    if (!matcher.find()) {
      throw new HalException(
          new ConfigProblemBuilder(Problem.Severity.FATAL, "Invalid plugin name: " + name).build());
    }

    if (!manifestVersion.equals(ManifestVersion.V1.getName())) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL, "Invalid manifest version for plugin: " + name)
              .build());
    }
  }

  public enum ManifestVersion {
    V1("plugins/v1");

    @Getter String name;

    @Override
    public String toString() {
      return this.name;
    }

    ManifestVersion(String name) {
      this.name = name;
    }
  }
}
