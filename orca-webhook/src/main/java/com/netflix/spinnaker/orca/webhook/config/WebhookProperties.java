/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.config;

import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredStageParameter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

@ConfigurationProperties("webhook")
@Data
@Slf4j
public class WebhookProperties {
  private static final Set<String> IGNORE_FIELDS =
      Set.of(
          "props",
          "enabled",
          "label",
          "description",
          "type",
          "parameters",
          "parameterValues",
          "permissions",
          "parameterData");
  private static final List<Field> ALL_FIELDS =
      Arrays.stream(PreconfiguredWebhook.class.getDeclaredFields())
          .filter(f -> !f.isSynthetic())
          .filter(f -> !IGNORE_FIELDS.contains(f.getName()))
          .collect(Collectors.toList());

  private List<PreconfiguredWebhook> preconfigured = new ArrayList<>();
  private TrustSettings trust = new TrustSettings();
  private IdentitySettings identity = new IdentitySettings();

  private boolean verifyRedirects = true;

  /** If true, follow redirects. If false, don't follow redirects. */
  private boolean followRedirects = true;

  private List<Integer> defaultRetryStatusCodes = List.of(429);

  // For testing *only*
  private boolean insecureSkipHostnameVerification = false;
  private boolean insecureTrustSelfSigned = false;

  /** True to enable matching against allowedRequests. */
  private boolean allowedRequestsEnabled = false;

  /**
   * Only specified http method + hosts are allowed. An empty list means no requests are allowed.
   */
  private List<AllowedRequest> allowedRequests = new ArrayList<>();

  /**
   * The maximum number of header + body bytes allowed in a webhook request, or <= 0 to allow any
   * size.
   */
  private long maxRequestBytes = 0L;

  /**
   * The maximum number of header + body bytes allowed in a webhook response, or <= 0 to allow any
   * size.
   */
  private long maxResponseBytes = 0L;

  /**
   * If the timeout expires before a connection can be established, a SocketTimeoutException is
   * raised. A timeout of 0 is considered infinite.
   */
  private long connectTimeoutMs = 15000L;

  /**
   * If the timeout expires before there is data available in the input stream to read, a
   * SocketTimeoutException is raised. A timeout of 0 is considered infinite.
   */
  private long readTimeoutMs = 20000L;

  /** True to enable audit logging */
  private boolean auditLoggingEnabled = false;

  @Data
  @NoArgsConstructor
  public static class TrustSettings {
    private boolean enabled;
    private String trustStore;
    private String trustStorePassword;
    // Default as JKS instead of PKCS12 for backward compatibility
    private String trustStoreType = "JKS";
    private String trustPem;
  }

  @Data
  @NoArgsConstructor
  public static class IdentitySettings {
    private boolean enabled;
    private String identityStore;
    private String identityStorePassword;
    private String identityStoreKeyPassword;
    private String identityStoreType = "PKCS12";

    private String identityKeyPem;
    private String identityCertPem;
  }

  @Data
  @NoArgsConstructor
  public static class AllowedRequest {
    /** The allowed http method(s) (e.g. GET, POST, PUT) */
    private List<String> httpMethods;

    /** The url must start with this string to be considered valid. */
    private String urlPrefix;
  }

  @Data
  @NoArgsConstructor
  public static class PreconfiguredWebhook {
    // Fields are public as webhook stages use reflection to access these directly from outside the
    // class
    public boolean enabled = true;
    public String label;
    public String description;
    public String type;
    public List<PreconfiguredStageParameter> parameters;

    // Stage configuration fields (all optional):
    public String url;
    public Map<String, List<String>> customHeaders;
    public Map<String, String> parameterValues;
    public Map<String, Map<String, String>> parameterData;
    public HttpMethod method;
    public String payload;
    public List<Integer> failFastStatusCodes;
    public Boolean waitForCompletion;
    public StatusUrlResolution statusUrlResolution;
    public String statusUrlJsonPath; // if webhookResponse above
    public String statusJsonPath;
    public String progressJsonPath;
    public String successStatuses;
    public String canceledStatuses;
    public String terminalStatuses;
    public Map<String, List<String>> permissions;
    public Boolean signalCancellation;
    public String cancelEndpoint;
    public HttpMethod cancelMethod;
    public String cancelPayload;

    public List<String> getPreconfiguredProperties() {
      return ALL_FIELDS.stream()
          .filter(
              f -> {
                try {
                  return f.get(this) != null;
                } catch (IllegalAccessException e) {
                  throw new RuntimeException(e);
                }
              })
          .map(Field::getName)
          .collect(Collectors.toList());
    }

    public boolean noUserConfigurableFields() {
      if (waitForCompletion == null) {
        return false;
      } else if (waitForCompletion) {
        return getPreconfiguredProperties().size()
            >= ALL_FIELDS.size()
                - (StatusUrlResolution.webhookResponse.equals(statusUrlResolution) ? 0 : 1);
      } else {
        return this.url != null
            && this.customHeaders != null
            && this.method != null
            && this.payload != null;
      }
    }

    public boolean isAllowed(String permission, Set<Role.View> roles) {
      if (permissions == null || !permissions.containsKey(permission)) {
        return true;
      }

      List<String> permissionList = permissions.get(permission);
      if (permissionList == null || permissionList.size() == 0) {
        return true;
      }

      return permissionList.stream().anyMatch(p -> anyRoleMatches(p, roles));
    }

    private boolean anyRoleMatches(String role, Set<Role.View> roles) {
      return roles.stream().anyMatch(r -> r.getName().contains(role));
    }
  }

  public enum StatusUrlResolution {
    getMethod,
    locationHeader,
    webhookResponse
  }
}
