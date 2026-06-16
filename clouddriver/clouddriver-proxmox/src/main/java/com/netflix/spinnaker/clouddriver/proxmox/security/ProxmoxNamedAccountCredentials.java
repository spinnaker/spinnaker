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
package com.netflix.spinnaker.clouddriver.proxmox.security;

import static lombok.EqualsAndHashCode.Include;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.proxmox.client.ProxmoxApiService;
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import com.netflix.spinnaker.config.ProxmoxConfigurationProperties;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.io.IOException;
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
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.util.ObjectUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ParametersAreNonnullByDefault
public class ProxmoxNamedAccountCredentials extends AbstractAccountCredentials<ProxmoxApiService> {
  private final String cloudProvider = "proxmox";

  @Nonnull @Include private final String name;

  private final String environment;
  private final String accountType;
  private final ProxmoxConfigurationProperties.ProxmoxManagedAccount managedAccount;
  private final Permissions permissions;
  private final ProxmoxApiService apiService;

  public ProxmoxNamedAccountCredentials(
      ProxmoxConfigurationProperties.ProxmoxManagedAccount managedAccount) {
    this.name = Objects.requireNonNull(managedAccount.getName());
    this.environment = "prod";
    this.accountType = "main";
    this.managedAccount = managedAccount;
    this.permissions = new Permissions.Builder().set(managedAccount.getPermissions()).build();
    this.apiService = buildApiService(managedAccount);
  }

  /**
   * This method is deprecated and users should instead supply {@link
   * ProxmoxNamedAccountCredentials#permissions}. In order to continue to support users who have
   * `requiredGroupMembership` in their account config, we still need to override this method. We'll
   * need to either communicate the backwards-incompatible change or translate the supplied
   * `requiredGroupMembership` into {@link ProxmoxNamedAccountCredentials#permissions} before
   * removing this override.
   */
  @Override
  @SuppressWarnings("deprecation")
  public List<String> getRequiredGroupMembership() {
    return Collections.emptyList();
  }

  @Override
  public ProxmoxApiService getCredentials() {
    return apiService;
  }

  private static ProxmoxApiService buildApiService(
      ProxmoxConfigurationProperties.ProxmoxManagedAccount account) {
    OkHttpClient httpClient = buildHttpClient(account);
    String baseUrl =
        String.format("https://%s:%d/api2/json/", account.getServer(), account.getPort());
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(ProxmoxApiService.class);
  }

  private static OkHttpClient buildHttpClient(
      ProxmoxConfigurationProperties.ProxmoxManagedAccount account) {
    OkHttpClient.Builder builder = new OkHttpClient.Builder();

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
        builder.sslSocketFactory(sslContext.getSocketFactory(), trustAll);
        builder.hostnameVerifier((hostname, session) -> true);
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        throw new RuntimeException("Failed to configure insecure SSL context", e);
      }
    }

    if (!ObjectUtils.isEmpty(account.getApiToken())) {
      String authHeader = "PVEAPIToken=" + account.getApiToken();
      builder.addInterceptor(
          chain -> {
            Request request =
                chain.request().newBuilder().header("Authorization", authHeader).build();
            return chain.proceed(request);
          });
    } else if (!ObjectUtils.isEmpty(account.getUserName())
        && !ObjectUtils.isEmpty(account.getPassword())) {
      TicketAuth ticket = fetchTicket(builder.build(), account);
      builder.addInterceptor(
          chain -> {
            Request request =
                chain
                    .request()
                    .newBuilder()
                    .header("Cookie", "PVEAuthCookie=" + ticket.ticket())
                    .header("CSRFPreventionToken", ticket.csrf())
                    .build();
            return chain.proceed(request);
          });
    }

    return builder.build();
  }

  private static TicketAuth fetchTicket(
      OkHttpClient client, ProxmoxConfigurationProperties.ProxmoxManagedAccount account) {
    String url =
        String.format(
            "https://%s:%d/api2/json/access/ticket", account.getServer(), account.getPort());
    okhttp3.RequestBody body =
        new FormBody.Builder()
            .add("username", account.getUserName())
            .add("password", account.getPassword())
            .build();
    Request request = new Request.Builder().url(url).post(body).build();
    try (okhttp3.Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new RuntimeException("Proxmox authentication failed: HTTP " + response.code());
      }
      JsonNode data = new ObjectMapper().readTree(response.body().string()).path("data");
      return new TicketAuth(
          data.path("ticket").asText(), data.path("CSRFPreventionToken").asText());
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to authenticate with Proxmox at " + account.getServer(), e);
    }
  }

  private record TicketAuth(String ticket, String csrf) {}
}
