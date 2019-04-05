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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.spinnaker.gate.services.commands.HystrixFactory;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.IgorService;
import groovy.transform.CompileStatic;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CompileStatic
@Component
public class ArtifactService {
  private static final String GROUP = "artifacts";

  private ClouddriverServiceSelector clouddriverServiceSelector;
  private IgorService igorService;

  @Autowired
  public ArtifactService(ClouddriverServiceSelector clouddriverServiceSelector, IgorService igorService) {
    this.clouddriverServiceSelector = clouddriverServiceSelector;
    this.igorService = igorService;
  }

  private static HystrixCommand<List<Map>> mapListCommand(String type, Callable<List<Map>> work) {
    return HystrixFactory.newListCommand(GROUP, type, work);
  }

  private static HystrixCommand<List<String>> stringListCommand(String type, Callable<List<String>> work) {
    return HystrixFactory.newListCommand(GROUP, type, work);
  }

  private static HystrixCommand<Void> voidCommand(String type, Callable<Void> work) {
    return HystrixFactory.newVoidCommand(GROUP, type, work);
  }

  public List<Map> getArtifactCredentials(String selectorKey) {
    return mapListCommand("artifactCredentials",
      clouddriverServiceSelector.select(selectorKey)::getArtifactCredentials)
      .execute();
  }

  public List<String> getArtifactNames(String selectorKey, String accountName, String type) {
    return stringListCommand("artifactNames",
      () -> clouddriverServiceSelector.select(selectorKey).getArtifactNames(accountName, type))
      .execute();
  }

  public List<String> getArtifactVersions(String selectorKey, String accountName, String type, String artifactName) {
    return stringListCommand("artifactVersions",
      () -> clouddriverServiceSelector.select(selectorKey).getArtifactVersions(accountName, type, artifactName))
      .execute();
  }

  public Void getArtifactContents(String selectorKey, Map<String, String> artifact, OutputStream outputStream) {
    return voidCommand("artifactContents", () -> {
      Response contentResponse = clouddriverServiceSelector.select(selectorKey).getArtifactContent(artifact);
      IOUtils.copy(contentResponse.getBody().in(), outputStream);
      return null;
    }).execute();
  }

  public List<String> getVersionsOfArtifactForProvider(String provider, String packageName) {
    return stringListCommand("artifactVersionsByProvider",
      () -> igorService.getArtifactVersions(provider, packageName))
      .execute();
  }
}
