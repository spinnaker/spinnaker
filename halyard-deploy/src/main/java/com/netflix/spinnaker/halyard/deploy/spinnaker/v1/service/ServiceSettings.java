/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

/**
 * These are the attributes of a service that can conceivably change between deployments to the
 * exact same deployment environment. Username and Password are easy examples, since these should be
 * updated with each deployment. Port and Address can be changed, so we'll keep these here too. On
 * the other hand, properties like Name, ArtifactType, etc... are all fixed, and can't be changed
 * between deployments. Ideally, if someone supplied ServiceSettings to Halyard, Halyard would have
 * everything it needs to run a deployment.
 *
 * <p>Warning: Do not define default values for the below fields in this class, since they will
 * override user-supplied values.
 */
@Data
public class ServiceSettings {
  Integer port;
  // A port open only to the internal network
  Integer internalPort;
  String address;
  String host;
  String scheme;
  String healthEndpoint;
  String username;
  String password;
  Map<String, String> env;
  String artifactId;
  String overrideBaseUrl;
  String location;
  KubernetesSettings kubernetes = new KubernetesSettings();
  Boolean enabled;
  Boolean basicAuthEnabled;
  Boolean monitored;
  Boolean sidecar;
  Boolean safeToUpdate;
  Integer targetSize;
  Boolean skipLifeCycleManagement;
  String baseUrl;

  public ServiceSettings() {}

  public ServiceSettings(String baseUrl) {
    this.enabled = true;
    this.baseUrl = baseUrl;
    this.kubernetes = null;
  }

  public ServiceSettings withOnlyBaseUrl() {
    return new ServiceSettings(getBaseUrl());
  }

  void mergePreferThis(ServiceSettings other) {
    Arrays.stream(getClass().getDeclaredMethods())
        .forEach(
            m -> {
              m.setAccessible(true);
              if (!m.getName().startsWith("get")) {
                return;
              }

              String setterName = "s" + m.getName().substring(1);
              Method s;
              try {
                s = getClass().getDeclaredMethod(setterName, m.getReturnType());
              } catch (NoSuchMethodException e) {
                return;
              }

              try {
                Object oThis = m.invoke(this);
                Object oOther = m.invoke(other);

                if (oThis == null) {
                  s.invoke(this, oOther);
                }
              } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(
                    "Unable to merge service settings: " + e.getMessage(), e);
              } finally {
                m.setAccessible(false);
              }
            });
    Map<String, String> fullEnvironment = new HashMap<>(other.getEnv());
    fullEnvironment.putAll(this.getEnv());
    this.setEnv(fullEnvironment);
  }

  public void decryptSecrets(SecretSessionManager secretSessionManager) {
    if (EncryptedSecret.isEncryptedSecret(password)) {
      password = secretSessionManager.decrypt(password);
    }

    env.replaceAll(
        (k, v) -> EncryptedSecret.isEncryptedSecret(v) ? secretSessionManager.decrypt(v) : v);
  }

  public String getBaseUrl() {
    return buildBaseUri().map(b -> b.toString()).orElse(null);
  }

  @JsonIgnore
  public String getAuthBaseUrl() {
    return buildBaseUri().get().setUserInfo(getUsername(), getPassword()).toString();
  }

  @JsonIgnore
  public String getMetricsUrl() {
    URIBuilder builder =
        new URIBuilder()
            .setScheme(getScheme())
            .setPort(getPort())
            .setHost("localhost")
            .setPath("spectator/metrics");

    if (getBasicAuthEnabled() != null && getBasicAuthEnabled()) {
      builder.setUserInfo(getUsername(), getPassword());
    }

    try {
      return builder.build().toString();
    } catch (URISyntaxException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Could not build metrics endpoint. This is probably a bug.", e);
    }
  }

  private Optional<URIBuilder> buildBaseUri() {
    if (!StringUtils.isBlank(overrideBaseUrl)) {
      try {
        return Optional.of(new URIBuilder(overrideBaseUrl));
      } catch (URISyntaxException e) {
        throw new HalException(
            Problem.Severity.FATAL, "Illegal override baseURL: " + overrideBaseUrl, e);
      }
    }
    if (!StringUtils.isBlank(baseUrl)) {
      try {
        return Optional.of(new URIBuilder(baseUrl));
      } catch (URISyntaxException e) {
        throw new HalException(Problem.Severity.FATAL, "Illegal baseURL: " + baseUrl, e);
      }
    }
    if (getScheme() != null && getPort() != null && getAddress() != null) {
      return Optional.of(
          new URIBuilder().setScheme(getScheme()).setPort(getPort()).setHost(getAddress()));
    }
    return Optional.empty();
  }

  public SlimServiceSettings slim() {
    return new SlimServiceSettings(getHost(), getPort(), getBaseUrl(), getEnabled());
  }

  @Data
  @AllArgsConstructor
  public static class SlimServiceSettings {
    private String host;
    private Integer port;
    private String baseUrl;
    private Boolean enabled;
  }
}
