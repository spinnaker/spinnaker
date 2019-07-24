/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.halyard.config.config.v1.ArtifactSourcesConfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.ValidForSpinnakerVersion;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.ContainerAccount;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@Data
@EqualsAndHashCode(callSuper = true)
public class KubernetesAccount extends ContainerAccount implements Cloneable {
  String context;
  String cluster;
  String user;

  @ValidForSpinnakerVersion(
      lowerBound = "1.5.0",
      tooLowMessage = "Spinnaker does not support configuring this behavior before that version.")
  Boolean configureImagePullSecrets;

  Boolean serviceAccount;
  int cacheThreads = 1;
  List<String> namespaces = new ArrayList<>();
  List<String> omitNamespaces = new ArrayList<>();

  @ValidForSpinnakerVersion(
      lowerBound = "1.7.0",
      tooLowMessage = "Configuring kind caching behavior is not supported yet.")
  List<String> kinds = new ArrayList<>();

  @ValidForSpinnakerVersion(
      lowerBound = "1.7.0",
      tooLowMessage = "Configuring kind caching behavior is not supported yet.")
  List<String> omitKinds = new ArrayList<>();

  @ValidForSpinnakerVersion(
      lowerBound = "1.6.0",
      tooLowMessage = "Custom kinds and resources are not supported yet.")
  List<CustomKubernetesResource> customResources = new ArrayList<>();

  @ValidForSpinnakerVersion(
      lowerBound = "1.8.0",
      tooLowMessage = "Caching policies are not supported yet.")
  List<KubernetesCachingPolicy> cachingPolicies = new ArrayList<>();

  @LocalFile @SecretFile String kubeconfigFile;
  String kubeconfigContents;
  String kubectlPath;
  Integer kubectlRequestTimeoutSeconds;
  Boolean checkPermissionsOnStartup;
  Boolean liveManifestCalls;

  // Without the annotations, these are written as `oauthServiceAccount` and `oauthScopes`,
  // respectively.
  @JsonProperty("oAuthServiceAccount")
  @LocalFile
  @SecretFile
  String oAuthServiceAccount;

  @JsonProperty("oAuthScopes")
  List<String> oAuthScopes;

  String namingStrategy;
  String skin;

  @JsonProperty("onlySpinnakerManaged")
  Boolean onlySpinnakerManaged;

  Boolean debug;

  public boolean usesServiceAccount() {
    return serviceAccount != null && serviceAccount;
  }

  public String getKubeconfigFile() {
    if (usesServiceAccount()) {
      return null;
    }

    if (kubeconfigFile == null || kubeconfigFile.isEmpty()) {
      return System.getProperty("user.home") + "/.kube/config";
    } else {
      return kubeconfigFile;
    }
  }

  @Override
  public void makeBootstrappingAccount(ArtifactSourcesConfig artifactSourcesConfig) {
    super.makeBootstrappingAccount(artifactSourcesConfig);

    DeploymentConfiguration deploymentConfiguration = parentOfType(DeploymentConfiguration.class);
    String location =
        StringUtils.isEmpty(deploymentConfiguration.getDeploymentEnvironment().getLocation())
            ? "spinnaker"
            : deploymentConfiguration.getDeploymentEnvironment().getLocation();

    // These changes are only surfaced in the account used by the bootstrapping clouddriver,
    // the user's clouddriver will be unchanged.
    if (!namespaces.isEmpty() && !namespaces.contains(location)) {
      namespaces.add(location);
    }

    if (!omitNamespaces.isEmpty() && omitNamespaces.contains(location)) {
      omitNamespaces.remove(location);
    }
  }

  @Data
  public static class CustomKubernetesResource {
    String kubernetesKind;
    String spinnakerKind;
    boolean versioned = false;
  }

  @Data
  public static class KubernetesCachingPolicy {
    String kubernetesKind;
    int maxEntriesPerAgent;
  }

  // These six methods exist for backwards compatibility. Versions of Halyard prior to 1.22 would
  // write this field out twice: to 'oAuthScopes' and to 'oauthScopes'. Whichever came last in the
  // file would end up taking precedence. These methods replicate that behavior during parsing, but
  // will only write out 'oAuthScopes' during serialization. They can be deleted after a few months
  // (at which point Lombok will generate the first four automatically). If you're reading this in
  // 2020 or later, you can definitely delete these (and also: whoah, the future is probably so fun,
  // how are those flying cars working out?)
  @JsonProperty("oAuthScopes")
  public List<String> getOAuthScopes() {
    return oAuthScopes;
  }

  @JsonProperty("oAuthScopes")
  public void setOAuthScopes(List<String> oAuthScopes) {
    this.oAuthScopes = oAuthScopes;
  }

  @JsonProperty("oAuthServiceAccount")
  public String getOAuthServiceAccount() {
    return oAuthServiceAccount;
  }

  @JsonProperty("oAuthServiceAccount")
  public void setOAuthServiceAccount(String oAuthServiceAccount) {
    this.oAuthServiceAccount = oAuthServiceAccount;
  }

  @JsonProperty("oauthScopes")
  public void setOauthScopes(List<String> oAuthScopes) {
    this.oAuthScopes = oAuthScopes;
  }

  @JsonProperty("oauthServiceAccount")
  public void setOauthServiceAccount(String oAuthServiceAccount) {
    this.oAuthServiceAccount = oAuthServiceAccount;
  }
}
