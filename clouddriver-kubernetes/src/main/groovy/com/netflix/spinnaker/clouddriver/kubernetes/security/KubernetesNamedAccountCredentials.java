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

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KubernetesNamedAccountCredentials implements AccountCredentials<KubernetesCredentials> {
    public KubernetesNamedAccountCredentials(String accountName, String environment, String accountType, String master, String username, String password, String namespace) {
      this(accountName, environment, accountType, master, username, password, namespace, null);
    }

    public KubernetesNamedAccountCredentials(String accountName, String environment, String accountType, String master, String username, String password, String namespace, List<String> requiredGroupMembership) {
      this.accountName = accountName;
      this.environment = environment;
      this.accountType = accountType;
      this.master = master;
      this.username = username;
      this.password = password;
      this.namespace = (namespace == null || namespace == "") ? "default" : namespace;
      // TODO(lwander): what is this?
      this.requiredGroupMembership = requiredGroupMembership == null ? Collections.emptyList() : Collections.unmodifiableList(requiredGroupMembership);
      this.credentials = buildCredentials();
    }

    @Override
    public String getName() {
      return accountName;
    }

    @Override
    public String getEnvironment() {
      return environment;
    }

    @Override
    public String getAccountType() {
      return accountType;
    }

    @Override
    public String getCloudProvider() {
      return CLOUD_PROVIDER;
    }

    public KubernetesCredentials getCredentials() {
      return credentials;
    }

    private KubernetesCredentials buildCredentials() {
      Config config = new ConfigBuilder().withMasterUrl(master).withUsername(username).withPassword(password).withTrustCerts(true).build();
      KubernetesClient client;
      try {
        client = new DefaultKubernetesClient(config);
      } catch (Exception e) {
        throw new RuntimeException("failed to create credentials", e);
      }
      return new KubernetesCredentials(this.namespace, client);
    }

    private static String getLocalName(String fullUrl) {
        return fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
    }

    @Override
    public String getProvider() {
      return getCloudProvider();
    }

    public String getAccountName() {
      return accountName;
    }

    public List<String> getRequiredGroupMembership() {
      return requiredGroupMembership;
    }

    private static final String CLOUD_PROVIDER = "kubernetes";
    private final String accountName;
    private final String environment;
    private final String accountType;
    private final String master;
    private final String username;
    private final String password;
    private final String namespace;
    private final KubernetesCredentials credentials;
    private final List<String> requiredGroupMembership;
}
