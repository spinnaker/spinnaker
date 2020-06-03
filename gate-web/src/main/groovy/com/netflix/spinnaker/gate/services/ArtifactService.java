/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.IgorService;
import groovy.transform.CompileStatic;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@CompileStatic
@Component
public class ArtifactService {

  private ClouddriverServiceSelector clouddriverServiceSelector;
  private Optional<IgorService> igorService;

  @Autowired
  public ArtifactService(
      ClouddriverServiceSelector clouddriverServiceSelector, Optional<IgorService> igorService) {
    this.clouddriverServiceSelector = clouddriverServiceSelector;
    this.igorService = igorService;
  }

  public List<Map> getArtifactCredentials(String selectorKey) {
    return clouddriverServiceSelector.select().getArtifactCredentials();
  }

  public List<String> getArtifactNames(String selectorKey, String accountName, String type) {
    return clouddriverServiceSelector.select().getArtifactNames(accountName, type);
  }

  public List<String> getArtifactVersions(
      String selectorKey, String accountName, String type, String artifactName) {
    return clouddriverServiceSelector.select().getArtifactVersions(accountName, type, artifactName);
  }

  @SneakyThrows
  public void getArtifactContents(
      String selectorKey, Map<String, String> artifact, OutputStream outputStream) {
    Response contentResponse = clouddriverServiceSelector.select().getArtifactContent(artifact);
    IOUtils.copy(contentResponse.getBody().in(), outputStream);
  }

  public List<String> getVersionsOfArtifactForProvider(
      String provider, String packageName, String releaseStatus) {
    if (!igorService.isPresent()) {
      throw new IllegalStateException(
          "Cannot fetch artifact versions because Igor is not enabled.");
    }

    return igorService.get().getArtifactVersions(provider, packageName, releaseStatus);
  }

  public Map<String, Object> getArtifactByVersion(
      String provider, String packageName, String version) {
    if (!igorService.isPresent()) {
      throw new IllegalStateException(
          "Cannot fetch artifact versions because Igor is not enabled.");
    }

    return igorService.get().getArtifactByVersion(provider, packageName, version);
  }
}
