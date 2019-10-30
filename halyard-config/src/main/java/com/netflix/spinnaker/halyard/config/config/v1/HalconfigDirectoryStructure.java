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

package com.netflix.spinnaker.halyard.config.config.v1;

import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HalconfigDirectoryStructure {
  private static ThreadLocal<String> directoryOverride = new ThreadLocal<>();

  public static void setDirectoryOverride(String directory) {
    directoryOverride.set(directory);
  }

  @Autowired @Setter String halconfigDirectory;

  public String getHalconfigDirectory() {
    String directory = directoryOverride.get();
    return directory == null ? halconfigDirectory : directory;
  }

  public String getHalconfigPath() {
    return normalizePath(Paths.get(getHalconfigDirectory(), "config").toString());
  }

  public Path getLogsPath(String deploymentName) {
    return ensureRelativeHalDirectory(deploymentName, "service-logs");
  }

  public Path getUserProfilePath(String deploymentName) {
    return ensureRelativeHalDirectory(deploymentName, "profiles");
  }

  public Path getServiceLogsPath(String deploymentName, String hostname, String serviceName) {
    Path halconfigPath = Paths.get(getLogsPath(deploymentName).toString(), hostname, serviceName);
    ensureDirectory(halconfigPath);
    return halconfigPath;
  }

  public Path getStagingPath(String deploymentName) {
    return ensureRelativeHalDirectory(deploymentName, "staging");
  }

  public Path getConfigPath(String deploymentName) {
    return ensureRelativeHalDirectory(deploymentName, "config");
  }

  public Path getStagingDependenciesPath(String deploymentName) {
    Path staging = getStagingPath(deploymentName);
    return ensureDirectory(Paths.get(staging.toString(), "dependencies"));
  }

  public Path getUserServiceSettingsPath(String deploymentName) {
    return ensureRelativeHalDirectory(deploymentName, "service-settings");
  }

  public Path getVaultTokenPath(String deploymentName) {
    Path halconfigPath = Paths.get(getHalconfigDirectory(), deploymentName);
    ensureDirectory(halconfigPath);
    return new File(halconfigPath.toFile(), "vault-token").toPath();
  }

  public Path getUnInstallScriptPath(String deploymentName) {
    Path halconfigPath = Paths.get(getHalconfigDirectory(), deploymentName);
    ensureDirectory(halconfigPath);
    return new File(halconfigPath.toFile(), "uninstall.sh").toPath();
  }

  public Path getPrepScriptPath(String deploymentName) {
    Path halconfigPath = Paths.get(getHalconfigDirectory(), deploymentName);
    ensureDirectory(halconfigPath);
    return new File(halconfigPath.toFile(), "prep.sh").toPath();
  }

  public Path getInstallScriptPath(String deploymentName) {
    Path halconfigPath = Paths.get(getHalconfigDirectory(), deploymentName);
    ensureDirectory(halconfigPath);
    return new File(halconfigPath.toFile(), "install.sh").toPath();
  }

  public Path getConnectScriptPath(String deploymentName) {
    Path halconfigPath = Paths.get(getHalconfigDirectory(), deploymentName);
    ensureDirectory(halconfigPath);
    return new File(halconfigPath.toFile(), "connect.sh").toPath();
  }

  public Path getHistoryPath(String deploymentName) {
    return ensureRelativeHalDirectory(deploymentName, "history");
  }

  public Path getCachePath() {
    return ensureDirectory(Paths.get(getHalconfigDirectory(), ".cache"));
  }

  public Path getSpinInstallScriptPath() {
    return ensureDirectory(Paths.get("/opt/spin/install/install-spin.sh"));
  }

  public Path getBackupConfigPath() {
    Path backup = ensureDirectory(Paths.get(getHalconfigDirectory(), ".backup"));
    return new File(backup.toFile(), "config").toPath();
  }

  public Path getBackupConfigDependenciesPath() {
    return ensureDirectory(Paths.get(getHalconfigDirectory(), ".backup", "required-files"));
  }

  public Path getServiceSettingsPath(String deploymentName) {
    File history = ensureRelativeHalDirectory(deploymentName, "history").toFile();
    return new File(history, "service-settings.yml").toPath();
  }

  public Path getServiceProfilesPath(String deploymentName) {
    File history = ensureRelativeHalDirectory(deploymentName, "history").toFile();
    return new File(history, "service-profiles.yml").toPath();
  }

  private Path ensureRelativeHalDirectory(String deploymentName, String directoryName) {
    Path path = Paths.get(getHalconfigDirectory(), deploymentName, directoryName);
    ensureDirectory(path);
    return path;
  }

  public Path ensureDirectory(Path path) {
    File file = path.toFile();
    if (file.exists()) {
      if (!file.isDirectory()) {
        throw new HalException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL, "The path " + path + " may not be a file.")
                .setRemediation(
                    "Please backup the file and remove it from your halconfig directory.")
                .build());
      }
    } else {
      try {
        if (!file.mkdirs()) {
          throw new HalException(
              new ConfigProblemBuilder(
                      Problem.Severity.FATAL,
                      "Error creating the directory " + path + " with unknown reason.")
                  .build());
        }
      } catch (Exception e) {
        throw new HalException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL,
                    "Error creating the directory " + path + ": " + e.getMessage())
                .build());
      }
    }

    return path;
  }

  private String normalizePath(String path) {
    String result = path.replaceFirst("^~", System.getProperty("user.home"));
    // Strip trailing path separator
    if (result.endsWith(File.separator)) {
      result = result.substring(0, result.length() - 1);
    }

    return result;
  }
}
