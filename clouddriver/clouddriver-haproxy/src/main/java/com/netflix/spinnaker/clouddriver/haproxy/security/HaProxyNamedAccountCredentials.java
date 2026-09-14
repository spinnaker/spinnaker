/*
 * Copyright 2026 McIntosh.farm
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
 */
package com.netflix.spinnaker.clouddriver.haproxy.security;

import static lombok.EqualsAndHashCode.Include;

import com.netflix.spinnaker.clouddriver.haproxy.HaProxyProvider;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.ApiClient;
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import com.netflix.spinnaker.config.HaProxyConfigurationProperties;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

@Slf4j
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ParametersAreNonnullByDefault
public class HaProxyNamedAccountCredentials extends AbstractAccountCredentials<ApiClient> {
  /** Security scheme name for basic auth in the Data Plane API OpenAPI specification. */
  private static final String BASIC_AUTH = "basic_auth";

  private final String cloudProvider = HaProxyProvider.ID;

  @Nonnull @Include private final String name;

  private final String environment;
  private final String accountType;
  private final HaProxyConfigurationProperties.HaProxyManagedAccount managedAccount;
  private final Permissions permissions;
  private final ApiClient apiClient;

  public HaProxyNamedAccountCredentials(
      HaProxyConfigurationProperties.HaProxyManagedAccount managedAccount) {
    this.name = Objects.requireNonNull(managedAccount).getName();
    this.environment = "prod";
    this.accountType = "main";
    this.managedAccount = managedAccount;
    this.permissions = new Permissions.Builder().set(managedAccount.getPermissions()).build();
    this.apiClient = buildApiClient(managedAccount);
  }

  /**
   * This method is deprecated and users should instead supply {@link
   * HaProxyNamedAccountCredentials#permissions}. In order to continue to support users who have
   * `requiredGroupMembership` in their account config, we still need to override this method.
   */
  @Override
  @SuppressWarnings("deprecation")
  public List<String> getRequiredGroupMembership() {
    return Collections.emptyList();
  }

  @Override
  public ApiClient getCredentials() {
    return apiClient;
  }

  public String getRegion() {
    return managedAccount.getRegion();
  }

  /** Convenience for creating a generated Data Plane API service, e.g. {@code FrontendApi}. */
  public <S> S getApi(Class<S> serviceClass) {
    return apiClient.createService(serviceClass);
  }

  private static ApiClient buildApiClient(
      HaProxyConfigurationProperties.HaProxyManagedAccount account) {
    ApiClient client;
    if (!ObjectUtils.isEmpty(account.getUserName())) {
      client = new ApiClient(BASIC_AUTH, account.getUserName(), account.getPassword());
    } else {
      client = new ApiClient();
    }

    // The generated APIs declare paths relative to the /v3 base path from the spec.
    String baseUrl =
        String.format(
            "%s://%s:%d/v3/", account.getScheme(), account.getServer(), account.getPort());
    client.getAdapterBuilder().baseUrl(baseUrl);

    if (account.isInsecure()) {
      X509TrustManager trustAll =
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          };
      try {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {trustAll}, null);
        client
            .getOkBuilder()
            .sslSocketFactory(sslContext.getSocketFactory(), trustAll)
            .hostnameVerifier((hostname, session) -> true);
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        throw new RuntimeException("Failed to configure insecure SSL context", e);
      }
    }

    return client;
  }
}
