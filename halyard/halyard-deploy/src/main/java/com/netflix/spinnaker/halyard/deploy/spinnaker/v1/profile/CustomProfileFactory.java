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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

@Component
public abstract class CustomProfileFactory extends ProfileFactory {
  protected abstract Path getUserProfilePath();

  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    profile.appendContents(profile.getBaseContents());
    // No modifications are made to user-supplied profiles.
  }

  @Override
  protected Profile getBaseProfile(String name, String version, String outputFile) {
    try {
      return new Profile(name, version, outputFile, readUserProfile());
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Unable to read user profile contents: " + e.getMessage(), e);
    }
  }

  private String readUserProfile() throws IOException {
    try (FileInputStream fis = new FileInputStream(getUserProfilePath().toFile())) {
      return IOUtils.toString(fis);
    } catch (FileNotFoundException e) {
      return "";
    }
  }

  @Override
  protected boolean showEditWarning() {
    return false;
  }

  @Override
  protected String commentPrefix() {
    return "";
  }
}
