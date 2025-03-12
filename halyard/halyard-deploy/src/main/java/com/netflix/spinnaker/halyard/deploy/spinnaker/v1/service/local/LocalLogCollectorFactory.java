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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DefaultLogCollector;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.LogCollector;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LocalLogCollectorFactory {
  @Autowired HalconfigDirectoryStructure directoryStructure;

  public <T> LogCollector<T, DeploymentDetails> build(HasServiceSettings<T> service) {
    return new LocalLogCollector<>(service);
  }

  public <T> LogCollector<T, DeploymentDetails> build(
      HasServiceSettings<T> service, String[] logPaths) {
    return new LocalLogCollector<>(service, logPaths);
  }

  private class LocalLogCollector<T> extends DefaultLogCollector<T, DeploymentDetails> {
    final List<Path> logPaths;

    LocalLogCollector(HasServiceSettings<T> service) {
      super(service);
      this.logPaths = new ArrayList<>();
      this.logPaths.add(Paths.get("/var/log/spinnaker/", getService().getCanonicalName()));
      this.logPaths.add(Paths.get("/var/log/upstart/", getService().getCanonicalName() + ".log"));
    }

    LocalLogCollector(HasServiceSettings<T> service, String[] logPaths) {
      super(service);
      this.logPaths = Arrays.stream(logPaths).map(c -> Paths.get(c)).collect(Collectors.toList());
    }

    @Override
    public void collectLogs(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
      File outputDir =
          directoryStructure
              .getServiceLogsPath(
                  details.getDeploymentName(), "localhost", getService().getCanonicalName())
              .toFile();

      for (Path path : logPaths) {
        File logFile = path.toFile();
        try {
          if (logFile.exists()) {
            log.warn("No logs file \"" + logFile + "\" found.");
          } else if (logFile.isDirectory()) {
            FileUtils.copyDirectoryToDirectory(logFile, outputDir);
          } else if (logFile.isFile()) {
            FileUtils.copyFileToDirectory(logFile, outputDir);
          } else {
            log.warn("Unknown file type " + logFile);
          }
        } catch (IOException e) {
          throw new HalException(
              Problem.Severity.FATAL, "Unable to copy logs: " + e.getMessage(), e);
        }
      }
    }
  }
}
