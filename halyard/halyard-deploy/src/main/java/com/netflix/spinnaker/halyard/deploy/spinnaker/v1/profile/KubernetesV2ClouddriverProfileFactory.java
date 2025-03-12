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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  // Constants used for parsing Kubeconfig file.
  private static final String CURRENT_CONTEXT = "current-context";
  private static final String CONTEXTS = "contexts";
  private static final String CONTEXT = "context";
  private static final String USERS = "users";
  private static final String USER = "user";
  private static final String NAME = "name";
  private static final String AUTH_PROVIDER = "auth-provider";
  private static final String CONFIG = "config";
  private static final String GCP = "gcp";

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
    // halyard-generated version.
    String kubeconfigFile = account.getKubeconfigFile();
    if (StringUtils.isEmpty(kubeconfigFile) || fileService.isRemoteFile(kubeconfigFile)) {
      return;
    }

    String kubeconfigContents = getKubeconfigFileContents(kubeconfigFile);

    Object obj = yamlParser.load(kubeconfigContents);
    Map<String, Object> parsedKubeconfig =
        objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    if (parsedKubeconfig == null) {
      throw new HalException(
          Problem.Severity.FATAL, "Empty kubeconfig file located at: " + kubeconfigFile);
    }

    final String context = getContext(account, kubeconfigFile, parsedKubeconfig);

    Optional<String> user = getKubeconfigUser(parsedKubeconfig, context);
    if (!user.isPresent()) {
      return;
    }

    Map<String, Object> userProperties = getKubeconfigUserProperties(parsedKubeconfig, user.get());

    Optional<Map<String, String>> authProviderConfig =
        getKubeconfigGcpAuthProviderConfig(userProperties);
    if (!authProviderConfig.isPresent()) {
      return;
    }

    authProviderConfig.get().put("cmd-path", "gcloud");

    try {
      yamlParser.dump(parsedKubeconfig, new FileWriter(kubeconfigFile));
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL,
          "Unable to write the kubeconfig file to the staging area. This may be a user permissions "
              + "issue.");
    }
  }

  private String getKubeconfigFileContents(String kubeconfigFile) {
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

  private static String getContext(
      KubernetesAccount account, String kubeconfigFile, Map<String, Object> parsedKubeconfig) {
    if (StringUtils.isNotEmpty(account.getContext())) {
      return account.getContext();
    }

    if (StringUtils.isNotEmpty((String) parsedKubeconfig.get(CURRENT_CONTEXT))) {
      return (String) parsedKubeconfig.get(CURRENT_CONTEXT);
    } else {
      throw new HalException(
          Problem.Severity.FATAL,
          "No context specified in kubernetes account "
              + account.getName()
              + " and no 'current-context' in "
              + kubeconfigFile);
    }
  }

  private static Optional<String> getKubeconfigUser(
      Map<String, Object> parsedKubeconfig, String contextName) {
    if (!parsedKubeconfig.containsKey(CONTEXTS)) {
      return Optional.empty();
    }
    List<Map<String, Object>> contexts = (List<Map<String, Object>>) parsedKubeconfig.get(CONTEXTS);

    Optional<Map<String, Object>> context =
        contexts.stream()
            .filter(c -> c.get(NAME).equals(contextName))
            .findFirst()
            .map(m -> (Map<String, Object>) m.get(CONTEXT));

    return context.isPresent()
        ? Optional.ofNullable((String) context.get().get(USER))
        : Optional.empty();
  }

  private static Map<String, Object> getKubeconfigUserProperties(
      Map<String, Object> parsedKubeconfig, String user) {
    if (parsedKubeconfig.containsKey(USERS)) {
      List<Map<String, Object>> users = (List<Map<String, Object>>) parsedKubeconfig.get(USERS);

      Optional<Object> userProperties =
          users.stream().filter(c -> c.get(NAME).equals(user)).findFirst().map(u -> u.get(USER));
      if (userProperties.isPresent()) {
        return (Map<String, Object>) userProperties.get();
      }
    }
    throw new HalException(
        Problem.Severity.FATAL, "No user named '" + user + "' exists in your kubeconfig file.");
  }

  private static Optional<Map<String, String>> getKubeconfigGcpAuthProviderConfig(
      Map<String, Object> userProperties) {
    if (!userProperties.containsKey(AUTH_PROVIDER)) {
      return Optional.empty();
    }
    Map<String, Object> authProvider = (Map<String, Object>) userProperties.get(AUTH_PROVIDER);

    return GCP.equals(authProvider.get(NAME))
        ? Optional.ofNullable((Map<String, String>) authProvider.get(CONFIG))
        : Optional.empty();
  }
}
