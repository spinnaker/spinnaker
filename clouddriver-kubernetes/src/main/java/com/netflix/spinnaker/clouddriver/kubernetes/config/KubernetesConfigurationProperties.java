/*
 * Copyright 2019 Google, Inc.
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

import java.util.List;
import lombok.Data;

@Data
public class KubernetesConfigurationProperties {
  private KubernetesJobExecutorProperties jobExecutor = new KubernetesJobExecutorProperties();

  /**
   * flag to toggle loading namespaces for a k8s account. By default, it is enabled, i.e., set to
   * true. Disabling it is meant primarily for making clouddriver start up faster, since no calls
   * are made to the k8s cluster to load these namespaces for accounts that are newly added
   */
  private boolean loadNamespacesInAccount = true;

  /** flag to toggle account health check. Defaults to true. */
  private boolean verifyAccountHealth = true;

  private Cache cache = new Cache();

  public KubernetesConfigurationProperties kubernetesConfigurationProperties() {
    return new KubernetesConfigurationProperties();
  }

  @Data
  public static class KubernetesJobExecutorProperties {
    private boolean persistTaskOutput = false;
    private boolean enableTaskOutputForAllAccounts = false;

    private Retries retries = new Retries();

    @Data
    public static class Retries {
      // flag to turn on/off kubectl retry on errors capability.
      private boolean enabled = false;

      // total number of attempts that are made to complete a kubectl call
      int maxAttempts = 3;

      // time in ms to wait before subsequent retry attempts
      long backOffInMs = 5000;

      // list of error strings on which to retry since kubectl binary returns textual error messages
      // back
      List<String> retryableErrorMessages = List.of("TLS handshake timeout");

      // flag to enable exponential backoff - only applicable when enableRetries: true
      boolean exponentialBackoffEnabled = false;

      // only applicable when exponentialBackoff = true
      int exponentialBackoffMultiplier = 2;

      // only applicable when exponentialBackoff = true
      long exponentialBackOffIntervalMs = 10000;
    }
  }

  @Data
  public static class Cache {

    /** Whether caching is enabled in the kubernetes provider. */
    private boolean enabled = true;

    /**
     * Whether to cache all kubernetes kinds or not. If this value is "true", the setting
     * "cacheKinds" is ignored.
     */
    private boolean cacheAll = false;

    /**
     * Only cache the kubernetes kinds in this list. If not configured, only the kinds that show in
     * Spinnaker's classic infrastructure screens are cached, which are the ones mapped to the
     * following Spinnaker's kinds: <br>
     * - SERVER_GROUP_MANAGERS <br>
     * - SERVER_GROUPS <br>
     * - INSTANCES <br>
     * - LOAD_BALANCERS <br>
     * - SECURITY_GROUPS
     *
     * <p>Names are in {kind.group} format, where the group is optional for core kinds. Example:
     * <br>
     * cacheKinds: <br>
     * - deployment.apps <br>
     * - replicaSet <br>
     * - pod <br>
     * - myCustomKind.my.group
     *
     * <p>If the setting {@link Cache#cacheAll} is true, this setting is ignored.
     */
    private List<String> cacheKinds = null;

    /**
     * Do not cache the kinds in this list. The format of the list is the same as {@link
     * Cache#cacheKinds}
     */
    private List<String> cacheOmitKinds = null;

    /**
     * controls whether an application name obtained from a kubernetes manifest needs to be checked
     * against front50. This can be needed in cases where we want front50 to be the definitive
     * source of truth for applications. If you set this to true, please ensure that front50 is
     * enabled.
     */
    boolean checkApplicationInFront50 = false;
  }
}
