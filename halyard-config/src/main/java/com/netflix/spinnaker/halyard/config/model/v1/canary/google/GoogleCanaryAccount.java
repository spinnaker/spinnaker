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

package com.netflix.spinnaker.halyard.config.model.v1.canary.google;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.config.v1.secrets.SecretSessionManager;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.SortedSet;
import java.util.TreeSet;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Slf4j
public class GoogleCanaryAccount extends AbstractCanaryAccount implements Cloneable {
  private String project;
  @LocalFile @SecretFile private String jsonPath;
  private String bucket;
  private String bucketLocation;
  private String rootFolder = "kayenta";
  private SortedSet<AbstractCanaryServiceIntegration.SupportedTypes> supportedTypes = new TreeSet<>();

  @JsonIgnore
  public GoogleNamedAccountCredentials getNamedAccountCredentials(String version, SecretSessionManager secretSessionManager, ConfigProblemSetBuilder p) {
    String jsonKey = null;
    if (!StringUtils.isEmpty(getJsonPath())) {
      jsonKey = secretSessionManager.validatingFileDecrypt(p, getJsonPath());

      if (jsonKey == null) {
        return null;
      } else if (jsonKey.isEmpty()) {
        p.addProblem(Problem.Severity.WARNING, "The supplied credentials file is empty.");
      }
    }

    if (StringUtils.isEmpty(getProject())) {
      p.addProblem(Problem.Severity.ERROR, "No google project supplied.");
      return null;
    }

    try {
      return new GoogleNamedAccountCredentials.Builder()
          .name(getName())
          .jsonKey(jsonKey)
          .project(getProject())
          .applicationName("halyard " + version)
          .liveLookupsEnabled(false)
          .build();
    } catch (Exception e) {
      p.addProblem(Problem.Severity.ERROR, "Error instantiating Google credentials: " + e.getMessage() + ".")
          .setRemediation("Do the provided credentials have access to project " + getProject() + "?");
      return null;
    }
  }
}
