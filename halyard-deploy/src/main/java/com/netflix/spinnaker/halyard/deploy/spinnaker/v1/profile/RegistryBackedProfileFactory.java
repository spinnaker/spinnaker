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

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.registry.v1.ProfileRegistry;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit.RetrofitError;

public abstract class RegistryBackedProfileFactory extends ProfileFactory {
  @Autowired ProfileRegistry profileRegistry;

  @Override
  protected Profile getBaseProfile(String name, String version, String outputFile) {
    try {
      return new Profile(
          name,
          version,
          outputFile,
          IOUtils.toString(profileRegistry.readProfile(getArtifact().getName(), version, name)));
    } catch (RetrofitError | IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(
                  FATAL, "Unable to retrieve profile \"" + name + "\": " + e.getMessage())
              .build(),
          e);
    }
  }
}
