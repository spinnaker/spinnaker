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
  @ValidForSpinnakerVersion(
      lowerBound = "",
      tooLowMessage = "",
      upperBound = "1.21.0",
      tooHighMessage =
          "The legacy (V1) Kubernetes provider is now deprecated. All accounts will "
              + "now be wired as standard (V2) accounts, and providerVersion can be removed from "
              + "all configured accounts.")
  ProviderVersion providerVersion = ProviderVersion.V2;

  String context;
  String cluster;
  String user;

  @ValidForSpinnakerVersion(
      lowerBound = "1.5.0",
      tooLowMessage = "Spinnaker does not support configuring this behavior before that version.")
  Boolean configureImagePullSecrets;

  Boolean serviceAccount;
  int cacheThreads = 1;
  Long cacheIntervalSeconds;
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
  RawResourcesEndpointConfig rawResourcesEndpointConfig = new RawResourcesEndpointConfig();
  Boolean cacheAllApplicationRelationships;

  @ValidForSpinnakerVersion(
      lowerBound = "1.12.0",
      tooLowMessage = "Live manifest mode not available prior to 1.12.0.",
      upperBound = "1.23.0",
      tooHighMessage = "Live manifest mode no longer necessary as of 1.23.0.")
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

  @Data
  public static class RawResourcesEndpointConfig {
    @JsonProperty("kindExpressions")
    List<String> kindExpressions;

    @JsonProperty("omitKindExpressions")
    List<String> omitKindExpressions;

    @JsonProperty("kindExpressions")
    public List<String> getKindExpressions() {
      return this.kindExpressions;
    }

    public void setKindExpressions(List<String> expressions) {
      this.kindExpressions = expressions;
    }

    @JsonProperty("omitKindExpressions")
    public List<String> getOmitKindExpressions() {
      return this.omitKindExpressions;
    }

    public void setOmitKindExpressions(List<String> expressions) {
      this.omitKindExpressions = expressions;
    }
  }

  @JsonProperty("rawResourcesEndpointConfig")
  public RawResourcesEndpointConfig getRawResourcesEndpointConfig() {
    return rawResourcesEndpointConfig;
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

  /**
   * @deprecated All ProviderVersion-related logic will be removed from Clouddriver by Spinnaker
   *     1.22. We will continue to support this enum in Halyard so that we can notify users with
   *     this field configured that it is no longer read.
   */
  @Deprecated
  public enum ProviderVersion {
    V1("v1"),
    V2("v2");

    private final String name;

    ProviderVersion(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }
}
