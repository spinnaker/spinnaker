/*
 *
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.echo.artifacts;

import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

import java.util.List;

/**
 * Given an artifact, fetch the details from an artifact provider
 */
public class ArtifactInfoService {

  private final IgorService igorService;

  public ArtifactInfoService(IgorService igorService) {
    this.igorService = igorService;
  }

  public List<String> getVersions(String provider, String packageName) {
    return igorService.getVersions(provider, packageName);
  }

  public Artifact getArtifactByVersion(String provider, String packageName, String version) {
    return igorService.getArtifactByVersion(provider, packageName, version);
  }
}
