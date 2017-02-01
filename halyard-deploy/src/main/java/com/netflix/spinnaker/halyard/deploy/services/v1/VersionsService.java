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

package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.Versions;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.registry.ProfileRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;

@Component
public class VersionsService {
  @Autowired
  ProfileRegistry profileRegistry;

  @Autowired
  Yaml yamlParser;

  @Autowired
  ObjectMapper objectMapper;

  public Versions getVersions() {
    try {
      return objectMapper.convertValue(
          yamlParser.load(profileRegistry.getObjectContents("versions.yml")),
          Versions.class
      );
    } catch (IOException e) {
      throw new HalconfigException(
          new ProblemBuilder(Problem.Severity.FATAL, "Could not load \"versions.yml\" from config bucket: " + e.getMessage() + ".").build());
    }
  }
}
