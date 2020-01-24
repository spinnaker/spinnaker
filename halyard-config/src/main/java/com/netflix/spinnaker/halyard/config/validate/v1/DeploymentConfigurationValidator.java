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
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment.DeploymentType;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeploymentConfigurationValidator extends Validator<DeploymentConfiguration> {
  @Autowired VersionsService versionsService;

  @Override
  public void validate(ConfigProblemSetBuilder p, DeploymentConfiguration n) {
    String timezone = n.getTimezone();

    if (Arrays.stream(TimeZone.getAvailableIDs()).noneMatch(t -> t.equals(timezone))) {
      p.addProblem(
              Problem.Severity.ERROR,
              "Timezone " + timezone + " does not match any known canonical timezone ID")
          .setRemediation(
              "Pick a timezone from those listed here: https://en.wikipedia.org/wiki/List_of_tz_database_time_zones");
    }

    validateVersions(p, n);
  }

  private void validateVersions(ConfigProblemSetBuilder p, DeploymentConfiguration n) {
    Versions versions = versionsService.getVersions();
    if (versions == null) {
      return;
    }

    String version = n.getVersion();

    boolean localGit = n.getDeploymentEnvironment().getType() == DeploymentType.LocalGit;

    if (StringUtils.isEmpty(version)) {
      p.addProblem(
          Problem.Severity.WARNING,
          "You have not yet selected a version of Spinnaker to deploy.",
          "version");
      return;
    }

    Optional<Versions.IllegalVersion> illegalVersion =
        versions.getIllegalVersions().stream()
            .filter(v -> v.getVersion().equals(version))
            .findAny();

    if (illegalVersion.isPresent()) {
      p.addProblem(
          Problem.Severity.ERROR,
          "Version \""
              + version
              + "\" may no longer be deployed with Halyard: "
              + illegalVersion.get().getReason());
      return;
    }

    if (Versions.isBranch(version) && !localGit) {
      p.addProblem(
              Problem.Severity.FATAL,
              "You can't run Spinnaker from a branch when your deployment type isn't \"LocalGit\".")
          .setRemediation(
              "Either pick a version (hal version list) or set a different deployment type (hal config deploy edit --type <t>).");
      return;
    }

    try {
      if (!Versions.isBranch(version)) {
        versionsService.getBillOfMaterials(version);
      }
    } catch (HalException e) {
      if (localGit) {
        p.addProblem(Problem.Severity.FATAL, "Could not fetch your desired version.")
            .setRemediation(
                "Is it possible that you're trying to checkout a branch? Prefix the version with \""
                    + Versions.BRANCH_PREFIX
                    + "\".");
        return;
      }
      p.extend(e);
      return;
    } catch (Exception e) {
      p.addProblem(
          Problem.Severity.FATAL,
          "Unexpected error trying to validate version \"" + version + "\": " + e.getMessage(),
          "version");
      return;
    }

    Optional<Versions.Version> releasedVersion =
        versions.getVersions().stream()
            .filter(v -> Objects.equals(v.getVersion(), version))
            .findFirst();

    boolean isReleased = releasedVersion.isPresent();

    String runningVersion = versionsService.getRunningHalyardVersion();
    boolean halyardSnapshotRelease = runningVersion.endsWith("SNAPSHOT");

    if (isReleased) {
      String minimumHalyardVersion = releasedVersion.get().getMinimumHalyardVersion();
      if (!localGit
          && !halyardSnapshotRelease
          && !StringUtils.isEmpty(minimumHalyardVersion)
          && Versions.lessThan(runningVersion, minimumHalyardVersion)) {
        p.addProblem(
            Problem.Severity.ERROR,
            "Halyard version \""
                + runningVersion
                + "\" is less than Halyard version \""
                + minimumHalyardVersion
                + "\" required for Spinnaker \""
                + version
                + "\"");
      }
    } else {
      // Checks if version is of the form X.Y.Z
      if (version.matches("\\d+\\.\\d+\\.\\d+")) {
        String majorMinor = Versions.toMajorMinor(version);
        Optional<Versions.Version> patchVersion =
            versions.getVersions().stream()
                .map(v -> new ImmutablePair<>(v, Versions.toMajorMinor(v.getVersion())))
                .filter(v -> v.getRight() != null)
                .filter(v -> v.getRight().equals(majorMinor))
                .map(ImmutablePair::getLeft)
                .findFirst();

        if (patchVersion.isPresent()) {
          p.addProblem(
                  Problem.Severity.WARNING,
                  "Version \""
                      + version
                      + "\" was patched by \""
                      + patchVersion.get().getVersion()
                      + "\". Please upgrade when possible.")
              .setRemediation("https://www.spinnaker.io/community/releases/versions/");
        } else {
          p.addProblem(
                  Problem.Severity.WARNING,
                  "Version \""
                      + version
                      + "\" is no longer supported by the Spinnaker team. Please upgrade when possible.")
              .setRemediation("https://www.spinnaker.io/community/releases/versions/");
        }
      } else {
        p.addProblem(
            Problem.Severity.WARNING,
            "Version \"" + version + "\" is not a released (validated) version of Spinnaker.",
            "version");
      }
    }
  }
}
