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

package com.netflix.spinnaker.halyard.core.registry.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProfileRegistry {
  @Autowired(required = false)
  GoogleProfileReader googleProfileReader;

  @Autowired GitProfileReader gitProfileReader;

  @Autowired LocalDiskProfileReader localDiskProfileReader;

  @Autowired ObjectMapper relaxedObjectMapper;

  public InputStream readProfile(String artifactName, String version, String profileName)
      throws IOException {
    return pickProfileReader(version).readProfile(artifactName, version, profileName);
  }

  public BillOfMaterials readBom(String version) throws IOException {
    return pickProfileReader(version).readBom(version);
  }

  public Versions readVersions() throws IOException {
    if (googleProfileReader == null) {
      return null;
    } else {
      // git can't store these
      return googleProfileReader.readVersions();
    }
  }

  public InputStream readArchiveProfile(String artifactName, String version, String profileName)
      throws IOException {
    return pickProfileReader(version).readArchiveProfile(artifactName, version, profileName);
  }

  private ProfileReader pickProfileReader(String version) {
    if (Versions.isBranch(version)) {
      return gitProfileReader;
    } else if (Versions.isLocal(version)) {
      return localDiskProfileReader;
    } else if (googleProfileReader != null) {
      return googleProfileReader;
    } else {
      throw new HalException(
          Problem.Severity.FATAL,
          "No profile reader exists to read '"
              + version
              + "'. Consider setting 'spinnaker.config.input.gcs.enabled: true' in /opt/spinnaker/config/halyard.yml");
    }
  }
}
