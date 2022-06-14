/*
 * Copyright 2021 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.kubernetes.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.security.AccessControlledAccountDefinition;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Previously, accounts were stored in the {@link KubernetesConfigurationProperties} class. If there
 * are loads of accounts defined in a configuration properties file, then letting Spring boot read
 * and bind them is a fairly time-consuming process. For 1500 accounts, we observed that it took
 * >10m to load them.
 *
 * <p>To speed this up, a feature-flagged change was introduced (see:
 * https://github.com/spinnaker/clouddriver/pull/5125) to let us do a manual binding of the
 * properties directly, instead of letting spring boot do it. This results in the load times
 * dropping to ~1-2s. But the main drawback of this manual binding is the fact that we have to
 * explicitly define all the properties that we need to bind. For example, if accounts are defined
 * in one configuration file and the other properties are defined in a different file, those other
 * properties will not be loaded unless they are defined in the same configuration file. Also, for
 * that to work, we have to explicitly bind these properties to the target class.
 *
 * <p>By moving accounts out of the {@link KubernetesConfigurationProperties} class, we don't need
 * to do any manual binding for those other properties. And we do the manual binding for accounts
 * only, which makes it more maintainable. Plus, this leaves us with a {@link
 * KubernetesConfigurationProperties} class which can cater to all the other configuration aspects
 * related to Kubernetes.
 */
@Data
public class KubernetesAccountProperties {
  private static final int DEFAULT_CACHE_THREADS = 1;

  @Data
  @JsonTypeName("kubernetes")
  public static class ManagedAccount implements AccessControlledAccountDefinition {
    private String name;
    private String environment;
    private String accountType;
    private String context;
    private String oAuthServiceAccount;
    private List<String> oAuthScopes;
    private String kubeconfigFile;
    private String kubeconfigContents;
    private String kubectlExecutable;
    private Integer kubectlRequestTimeoutSeconds;
    private boolean serviceAccount = false;
    private List<String> namespaces = new ArrayList<>();
    private List<String> omitNamespaces = new ArrayList<>();
    private int cacheThreads = DEFAULT_CACHE_THREADS;
    private List<String> requiredGroupMembership = new ArrayList<>();
    private Permissions.Builder permissions = new Permissions.Builder();
    private String namingStrategy = "kubernetesAnnotations";
    private boolean debug = false;
    private boolean metrics = true;
    private boolean checkPermissionsOnStartup = true;
    private List<CustomKubernetesResource> customResources = new ArrayList<>();
    private List<KubernetesCachingPolicy> cachingPolicies = new ArrayList<>();
    private List<String> kinds = new ArrayList<>();
    private List<String> omitKinds = new ArrayList<>();
    private boolean onlySpinnakerManaged = false;
    private Long cacheIntervalSeconds;
    private boolean cacheAllApplicationRelationships = false;
    private RawResourcesEndpointConfig rawResourcesEndpointConfig =
        new RawResourcesEndpointConfig();

    public void validate() {
      if (Strings.isNullOrEmpty(name)) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
      }

      if (!omitNamespaces.isEmpty() && !namespaces.isEmpty()) {
        throw new IllegalArgumentException(
            "At most one of 'namespaces' and 'omitNamespaces' can be specified");
      }

      if (!omitKinds.isEmpty() && !kinds.isEmpty()) {
        throw new IllegalArgumentException(
            "At most one of 'kinds' and 'omitKinds' can be specified");
      }
      rawResourcesEndpointConfig.validate();
    }
  }

  private List<ManagedAccount> accounts = new ArrayList<>();
}
