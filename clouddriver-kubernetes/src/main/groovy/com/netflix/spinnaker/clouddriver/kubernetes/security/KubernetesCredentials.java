/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class KubernetesCredentials {
  private final KubernetesApiAdaptor apiAdaptor;
  private final List<String> namespaces;
  private final List<LinkedDockerRegistryConfiguration> dockerRegistries;
  private final HashMap<String, List<String>> imagePullSecrets;
  private final Logger LOG;

  public KubernetesCredentials(KubernetesApiAdaptor apiAdaptor,
                               List<String> namespaces,
                               List<LinkedDockerRegistryConfiguration> dockerRegistries,
                               AccountCredentialsRepository accountCredentialsRepository) {
    this.apiAdaptor = apiAdaptor;
    this.namespaces = namespaces != null ? namespaces : new ArrayList<>();
    this.dockerRegistries = dockerRegistries != null ? dockerRegistries : new ArrayList<>();
    this.imagePullSecrets = new HashMap<>();
    this.LOG = LoggerFactory.getLogger(KubernetesCredentials.class);

    for (int i = 0; i < this.dockerRegistries.size(); i++) {
      LinkedDockerRegistryConfiguration registry = this.dockerRegistries.get(i);
      this.LOG.info("Adding secrets for docker registry " + registry.getAccountName() + ".");
      List<String> registryNamespaces = registry.getNamespaces();
      List<String> affectedNamespaces = registryNamespaces != null && registryNamespaces.size() > 0 ? registryNamespaces : this.namespaces;

      DockerRegistryNamedAccountCredentials account = (DockerRegistryNamedAccountCredentials) accountCredentialsRepository.getOne(registry.getAccountName());

      if (account == null) {
        throw new IllegalArgumentException("The account " + registry.getAccountName() + " was not configured inside Clouddriver.");
      }

      for (int j = 0; j < affectedNamespaces.size(); j++) {
        String inNamespace = affectedNamespaces.get(j);
        SecretBuilder secretBuilder = new SecretBuilder();
        String secretName = registry.getAccountName();

        Secret exists = this.apiAdaptor.getSecret(inNamespace, secretName);
        if (exists != null) {
          this.LOG.info("Secret for docker registry " + registry.getAccountName() + " in namespace " + inNamespace + " is being repopulated.");
          this.apiAdaptor.deleteSecret(inNamespace, secretName);
        }

        secretBuilder = secretBuilder.withNewMetadata().withName(secretName).endMetadata();

        HashMap<String, String> secretData = new HashMap<>(1);
        String dockerCfg = String.format("{ \"%s\": { \"auth\": \"%s\", \"email\": \"%s\" } }", account.getV2Endpoint(), account.getBasicAuth(), account.getEmail());
        try {
          dockerCfg = new String(Base64.getEncoder().encode(dockerCfg.getBytes("UTF-8")), "UTF-8");
        } catch (UnsupportedEncodingException uee) {
          throw new IllegalStateException("Unable to encode docker config ", uee);
        }
        secretData.put(".dockercfg", dockerCfg);

        secretBuilder = secretBuilder.withData(secretData).withType("kubernetes.io/dockercfg");
        this.apiAdaptor.createSecret(inNamespace, secretBuilder.build());

        List<String> existingSecrets = imagePullSecrets.get(inNamespace);
        existingSecrets = existingSecrets != null ? existingSecrets : new ArrayList<>();
        existingSecrets.add(secretName);
        imagePullSecrets.put(inNamespace, existingSecrets);
      }
    }
  }

  public KubernetesApiAdaptor getApiAdaptor() {
    return apiAdaptor;
  }

  public List<String> getNamespaces() {
    return namespaces;
  }

  public List<LinkedDockerRegistryConfiguration> getDockerRegistries() {
    return dockerRegistries;
  }

  public Map<String, List<String>> getImagePullSecrets() {
    return imagePullSecrets;
  }

  public Boolean isRegisteredNamespace(String namespace) {
    return namespaces.contains(namespace);
  }

  public Boolean isRegisteredImagePullSecret(String secret, String namespace) {
    List<String> secrets = imagePullSecrets.get(namespace);
    if (secrets == null) {
      return false;
    }
    return secrets.contains(secret);
  }
}
