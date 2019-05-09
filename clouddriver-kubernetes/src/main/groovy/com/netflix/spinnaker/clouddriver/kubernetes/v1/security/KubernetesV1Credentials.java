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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.security;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesApiClientConfig;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesClientApiAdapter;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolationException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KubernetesV1Credentials implements KubernetesCredentials {
  private final KubernetesApiAdaptor apiAdaptor;
  private KubernetesClientApiAdapter apiClientAdaptor;
  private final List<String> namespaces;
  private final List<String> omitNamespaces;
  private final List<LinkedDockerRegistryConfiguration> dockerRegistries;
  private final HashMap<String, Set<String>> imagePullSecrets = new HashMap<>();
  private final Logger LOG;
  private final AccountCredentialsRepository repository;
  private final HashSet<String> dynamicRegistries = new HashSet<>();
  private final boolean configureImagePullSecrets;
  private List<String> oldNamespaces;

  public KubernetesV1Credentials(
      String name,
      String kubeconfigFile,
      String context,
      String cluster,
      String user,
      String userAgent,
      Boolean serviceAccount,
      boolean configureImagePullSecrets,
      List<String> namespaces,
      List<String> omitNamespaces,
      List<LinkedDockerRegistryConfiguration> dockerRegistries,
      Registry spectatorRegistry,
      AccountCredentialsRepository accountCredentialsRepository) {
    if (dockerRegistries == null || dockerRegistries.size() == 0) {
      throw new IllegalArgumentException("Docker registries for Kubernetes account " + name + " are required.");
    }

    Config config = KubernetesConfigParser.parse(kubeconfigFile, context, cluster, user, namespaces, serviceAccount);
    config.setUserAgent(userAgent);

    KubernetesApiClientConfig configClient = new KubernetesApiClientConfig(kubeconfigFile, context, cluster, user, userAgent, serviceAccount);

    this.apiAdaptor = new KubernetesApiAdaptor(name, config, spectatorRegistry);
    this.apiClientAdaptor = new KubernetesClientApiAdapter(name, configClient, spectatorRegistry);
    this.namespaces = namespaces != null ? namespaces : new ArrayList<>();
    this.omitNamespaces = omitNamespaces != null ? omitNamespaces : new ArrayList<>();
    this.dockerRegistries = dockerRegistries;
    this.repository = accountCredentialsRepository;
    this.LOG = LoggerFactory.getLogger(KubernetesV1Credentials.class);
    this.configureImagePullSecrets = configureImagePullSecrets;

    configureDockerRegistries();
  }

  @VisibleForTesting
  protected KubernetesV1Credentials(
      KubernetesApiAdaptor apiAdaptor,
      List<String> namespaces,
      List<String> omitNamespaces,
      List<LinkedDockerRegistryConfiguration> dockerRegistries,
      AccountCredentialsRepository repository) {
    this.apiAdaptor = apiAdaptor;
    this.namespaces = namespaces != null ? namespaces : new ArrayList<>();
    this.omitNamespaces = omitNamespaces != null ? omitNamespaces : new ArrayList<>();
    this.dockerRegistries = dockerRegistries;
    this.repository = repository;
    this.LOG = LoggerFactory.getLogger(KubernetesV1Credentials.class);
    this.configureImagePullSecrets = true;

    configureDockerRegistries();
  }

  private void configureDockerRegistries() {
    oldNamespaces = namespaces;

    for (LinkedDockerRegistryConfiguration dockerRegistryConfiguration : dockerRegistries) {
      if (dockerRegistryConfiguration.getNamespaces() == null || dockerRegistryConfiguration.getNamespaces().isEmpty()) {
        dynamicRegistries.add(dockerRegistryConfiguration.getAccountName());
      }
    }

    try {
      List<String> knownNamespaces = !namespaces.isEmpty() ? namespaces : apiAdaptor.getNamespacesByName();
      reconfigureRegistries(knownNamespaces);
    } catch (Exception e) {
      LOG.warn("Could not determine kubernetes namespaces. Will try again later.", e);
    }

  }

  public List<String> getDeclaredNamespaces() {
    try {
      if (namespaces != null && !namespaces.isEmpty()) {
        // If namespaces are provided, use them
        reconfigureRegistries(namespaces);
        return namespaces;
      } else {
        List<String> addedNamespaces = apiAdaptor.getNamespacesByName();
        addedNamespaces.removeAll(omitNamespaces);

        List<String> resultNamespaces = new ArrayList<>(addedNamespaces);

        // Find the namespaces that were added, and add docker secrets to them. No need to track deleted
        // namespaces since they delete their secrets automatically.
        addedNamespaces.removeAll(oldNamespaces);
        reconfigureRegistries(resultNamespaces);
        oldNamespaces = resultNamespaces;

        return resultNamespaces;
      }
    } catch (Exception e) {
      LOG.warn("Could not determine kubernetes namespaces. Will try again later.", e);
      return Lists.newArrayList();
    }
  }

  private void reconfigureRegistries(List<String> allNamespaces) {
    List<String> affectedNamespaces = new ArrayList<>(allNamespaces);
    if (!configureImagePullSecrets) {
      return;
    }

    // only initialize namespaces that haven't been initialized yet.
    List<String> initializedNamespaces = new ArrayList<>(imagePullSecrets.keySet());
    affectedNamespaces.removeAll(initializedNamespaces);

    for (int i = 0; i < dockerRegistries.size(); i++) {
      LinkedDockerRegistryConfiguration registry = dockerRegistries.get(i);
      List<String> registryNamespaces = registry.getNamespaces();
      // If a registry was not initially configured with any namespace, it can deploy to any namespace, otherwise
      // restrict the deploy to the registryNamespaces
      if (!dynamicRegistries.contains(registry.getAccountName())) {
        affectedNamespaces = registryNamespaces;
      } else {
        registry.setNamespaces(allNamespaces);
      }

      if (affectedNamespaces != null && !affectedNamespaces.isEmpty()) {
        LOG.debug("Adding secrets for docker registry {} in {}", registry.getAccountName(), affectedNamespaces);
      }

      DockerRegistryNamedAccountCredentials account = (DockerRegistryNamedAccountCredentials) repository.getOne(registry.getAccountName());

      if (account == null) {
        LOG.warn("The account " + registry.getAccountName() + " was not yet loaded inside Clouddriver. If you are seeing this message repeatedly, it likely cannot be loaded.");
        continue;
      }

      for (String namespace : affectedNamespaces) {
        Namespace res = apiAdaptor.getNamespace(namespace);
        if (res == null) {
          NamespaceBuilder namespaceBuilder = new NamespaceBuilder();
          Namespace newNamespace = namespaceBuilder.withNewMetadata().withName(namespace).endMetadata().build();
          apiAdaptor.createNamespace(newNamespace);
        }

        SecretBuilder secretBuilder = new SecretBuilder();
        String secretName = registry.getAccountName();

        secretBuilder = secretBuilder.withNewMetadata().withName(secretName).withNamespace(namespace).endMetadata();

        HashMap<String, String> secretData = new HashMap<>(1);
        String dockerCfg = String.format("{ \"%s\": { \"auth\": \"%s\", \"email\": \"%s\" } }",
                                         account.getAddress(),
                                         account.getBasicAuth(),
                                         account.getEmail());

        try {
          dockerCfg = new String(Base64.getEncoder().encode(dockerCfg.getBytes("UTF-8")), "UTF-8");
        } catch (UnsupportedEncodingException uee) {
          throw new IllegalStateException("Unable to encode docker config ", uee);
        }
        secretData.put(".dockercfg", dockerCfg);

        secretBuilder = secretBuilder.withData(secretData).withType("kubernetes.io/dockercfg");
        try {
          Secret newSecret = secretBuilder.build();
          Secret oldSecret = apiAdaptor.getSecret(namespace, secretName);
          if (oldSecret != null) {
            if (oldSecret.getData().equals(newSecret.getData())) {
              LOG.debug("Skipping creation of duplicate secret " + secretName + " in namespace " + namespace);
            } else {
              apiAdaptor.editSecret(namespace, secretName).addToData(newSecret.getData()).done();
            }
          } else {
            apiAdaptor.createSecret(namespace, secretBuilder.build());
          }
        } catch (ConstraintViolationException cve) {
          throw new IllegalStateException("Unable to build secret: " + cve.getMessage() +
                                          " due to violations " + cve.getConstraintViolations(),
                                          cve);
        }

        Set<String> existingSecrets = imagePullSecrets.get(namespace);
        existingSecrets = existingSecrets != null ? existingSecrets : new HashSet<>();
        existingSecrets.add(secretName);
        imagePullSecrets.put(namespace, existingSecrets);
      }
    }
  }

  public KubernetesApiAdaptor getApiAdaptor() {
    return apiAdaptor;
  }

  public KubernetesClientApiAdapter getClientApiAdaptor() {
    return apiClientAdaptor;
  }


  public List<LinkedDockerRegistryConfiguration> getDockerRegistries() {
    return dockerRegistries;
  }

  public Map<String, Set<String>> getImagePullSecrets() {
    return imagePullSecrets;
  }

  public Boolean isRegisteredNamespace(String namespace) {
    return getDeclaredNamespaces().contains(namespace);
  }

  public Boolean isRegisteredImagePullSecret(String secret, String namespace) {
    Set<String> secrets = imagePullSecrets.get(namespace);
    if (secrets == null) {
      return false;
    }
    return secrets.contains(secret);
  }
}
