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

package com.netflix.spinnaker.halyard.config.validate.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class DeploymentConfigurationValidator extends Validator<DeploymentConfiguration> {
  @Autowired
  VersionsService versionsService;

  @Override
  public void validate(ConfigProblemSetBuilder p, DeploymentConfiguration n) {
    String version = n.getVersion();
    Versions versions = versionsService.getVersions();

    Optional<Versions.IllegalVersion> illegalVersion = versions.getIllegalVersions()
        .stream()
        .filter(v -> v.getVersion().equals(version))
        .findAny();

    if (illegalVersion.isPresent()) {
      p.addProblem(Problem.Severity.ERROR, "Version \"" + version + "\" may no longer be deployed with Halyard: " + illegalVersion.get().getReason());
      return;
    }

    try {
      versionsService.getBillOfMaterials(version);
    } catch (HalException e) {
      p.extend(e);
      return;
    } catch (Exception e) {
      p.addProblem(Problem.Severity.FATAL, "Unexpected error trying to validate version \"" + version + "\": " + e.getMessage(), "version");
      return;
    }

    boolean isReleased = versions.getVersions()
        .stream()
        .anyMatch(v -> Objects.equals(v.getVersion(), version));

    if (!isReleased) {
      // Checks if version is of the form X.Y.Z
      if (version.matches("\\d+\\.\\d+\\.\\d+")) {
        String majorMinor = toMajorMinor(version);
        Optional<Versions.Version> patchVersion = versions.getVersions()
            .stream()
            .map(v -> new ImmutablePair<>(v, toMajorMinor(v.getVersion())))
            .filter(v -> v.getRight() != null)
            .filter(v -> v.getRight().equals(majorMinor))
            .map(ImmutablePair::getLeft)
            .findFirst();

        if (patchVersion.isPresent()) {
          p.addProblem(Problem.Severity.WARNING, "Version \"" + version + "\" was patched by \"" + patchVersion.get().getVersion() + "\". Please upgrade when possible.")
              .setRemediation("https://spinnaker.github.io/setup/install/upgrades/");
        } else {
          p.addProblem(Problem.Severity.WARNING, "Version \"" + version + "\" is no longer supported by the Spinnaker team. Please upgrade when possible.")
              .setRemediation("https://spinnaker.github.io/setup/install/upgrades/");
        }
      } else {
        p.addProblem(Problem.Severity.WARNING, "Version \"" + version + "\" is not a released (validated) version of Spinnaker.", "version");
      }
    }
  }

  public static String toMajorMinor(String fullVersion) {
    int lastDot = fullVersion.lastIndexOf(".");
    if (lastDot < 0) {
      return null;
    }

    return fullVersion.substring(0, lastDot);
  }
}
