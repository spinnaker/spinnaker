/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.services.v1.FileService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
@Slf4j
public class KubernetesV2ClouddriverProfileFactory extends ClouddriverProfileFactory {

  private final ObjectMapper objectMapper;
  private final Yaml yamlParser;
  private final FileService fileService;

  public KubernetesV2ClouddriverProfileFactory(
      ObjectMapper objectMapper, Yaml yamlParser, FileService fileService) {
    this.objectMapper = objectMapper;
    this.yamlParser = yamlParser;
    this.fileService = fileService;
  }

  @Override
  protected void processProviders(Providers providers) {
    if (providers.getKubernetes() != null && providers.getKubernetes().getAccounts() != null) {
      providers.getKubernetes().getAccounts().forEach(this::processKubernetesAccount);
    }
  }

  private void processKubernetesAccount(KubernetesAccount account) {
    if (account.usesServiceAccount()) {
      return;
    }

    // If kubeconfigFile is remote, clouddriver will download it at runtime instead of using a
    // halyard-generated version
    String kubeconfigFile = account.getKubeconfigFile();
    if (StringUtils.isEmpty(kubeconfigFile) || fileService.isRemoteFile(kubeconfigFile)) {
      return;
    }

    String kubeconfigContents = getKubconfigFileContents(kubeconfigFile);
    String context = account.getContext();

    Object obj = yamlParser.load(kubeconfigContents);
    Map<String, Object> parsedKubeconfig =
        objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});

    if (StringUtils.isEmpty(context)) {
      context = (String) parsedKubeconfig.get("current-context");
    }

    if (StringUtils.isEmpty(context)) {
      throw new HalException(
          Problem.Severity.FATAL,
          "No context specified in kubernetes account "
              + account.getName()
              + " and no 'current-context' in "
              + kubeconfigFile);
    }

    final String finalContext = context;

    String user =
        (String)
            ((List<Map<String, Object>>)
                    parsedKubeconfig.getOrDefault("contexts", new ArrayList<>()))
                .stream()
                    .filter(c -> c.getOrDefault("name", "").equals(finalContext))
                    .findFirst()
                    .map(
                        m ->
                            ((Map<String, Object>) m.getOrDefault("context", new HashMap<>()))
                                .get("user"))
                    .orElse("");

    if (StringUtils.isEmpty(user)) {
      return;
    }

    Map<String, Object> userProperties =
        (Map<String, Object>)
            ((List<Map<String, Object>>) parsedKubeconfig.getOrDefault("users", new ArrayList<>()))
                .stream()
                    .filter(c -> c.getOrDefault("name", "").equals(user))
                    .findFirst()
                    .map(u -> u.get("user"))
                    .orElse(null);

    if (userProperties == null) {
      throw new HalException(
          Problem.Severity.FATAL, "No user named '" + user + "' exists in your kubeconfig file.");
    }

    Map<String, Object> authProvider = (Map<String, Object>) userProperties.get("auth-provider");

    if (authProvider == null || !authProvider.getOrDefault("name", "").equals("gcp")) {
      return;
    }

    Map<String, String> authProviderConfig = (Map<String, String>) authProvider.get("config");

    if (authProviderConfig == null) {
      return;
    }

    authProviderConfig.put("cmd-path", "gcloud");

    try {
      yamlParser.dump(parsedKubeconfig, new FileWriter(kubeconfigFile));
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL,
          "Unable to write the kubeconfig file to the staging area. This may be a user permissions issue.");
    }
  }

  private String getKubconfigFileContents(String kubeconfigFile) {
    try {
      return fileService.getFileContents(kubeconfigFile);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read kubeconfig file '"
              + kubeconfigFile
              + "', but validation passed: "
              + e.getMessage(),
          e);
    }
  }
}
