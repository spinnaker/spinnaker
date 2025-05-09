/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.docker.service;

import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.docker.exceptions.DockerRegistryAuthenticationException;
import com.netflix.spinnaker.kork.docker.model.DockerBearerToken;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import io.micrometer.core.instrument.util.IOUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerBearerTokenService {
  private static final Logger log = LoggerFactory.getLogger(DockerBearerTokenService.class);

  private final Map<String, RegistryService> realmToService = new HashMap<>();
  private final Map<String, DockerBearerToken> cachedTokens = new HashMap<>();
  private String username;
  private String password;
  private String passwordCommand;
  private File passwordFile;
  private String authWarning;
  private final ServiceClientProvider serviceClientProvider;

  private static final String USER_AGENT = DockerUserAgent.getUserAgent();

  public DockerBearerTokenService(ServiceClientProvider serviceClientProvider) {
    this.serviceClientProvider = serviceClientProvider;
  }

  public DockerBearerTokenService(
      String username,
      String password,
      String passwordCommand,
      ServiceClientProvider serviceClientProvider) {
    this(serviceClientProvider);
    this.username = username;
    this.password = password;
    this.passwordCommand = passwordCommand;
  }

  public DockerBearerTokenService(
      String username, File passwordFile, ServiceClientProvider serviceClientProvider) {
    this(serviceClientProvider);
    this.username = username;
    this.passwordFile = passwordFile;
  }

  public String getBasicAuth() {
    if (isAllAuthFieldsEmpty()) {
      return null;
    }
    String resolvedPassword = resolvePassword();
    checkPasswordWhitespace(resolvedPassword);
    String authString = username + ":" + resolvedPassword;
    return Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
  }

  private boolean isAllAuthFieldsEmpty() {
    return (username == null || username.isEmpty())
        && (password == null || password.isEmpty())
        && (passwordCommand == null || passwordCommand.isEmpty())
        && passwordFile == null;
  }

  private String resolvePassword() {
    try {
      if (password != null && !password.isEmpty()) {
        return password;
      } else if (passwordCommand != null && !passwordCommand.isEmpty()) {
        return resolvePasswordFromCommand();
      } else if (passwordFile != null) {
        return resolvePasswordFromFile();
      } else {
        return "";
      }
    } catch (Exception e) {
      log.error("Error resolving password: {}", e.getMessage());
      throw new DockerRegistryAuthenticationException(
          "Error resolving password: " + e.getMessage(), e);
    }
  }

  private String resolvePasswordFromCommand() throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", passwordCommand);
    Process process = processBuilder.start();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      String errorOutput = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8);
      log.error("Password command returned non-zero exit code. Stderr/stdout: '{}'", errorOutput);
    }
    return IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim();
  }

  private String resolvePasswordFromFile() throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(passwordFile))) {
      return reader.readLine().trim();
    }
  }

  private void checkPasswordWhitespace(String resolvedPassword) {
    if (resolvedPassword != null && !resolvedPassword.isEmpty()) {
      String message =
          "Your registry password has %s whitespace, if this is unintentional authentication will fail.";
      if (Character.isWhitespace(resolvedPassword.charAt(0))) {
        authWarning = String.format(message, "leading");
      }
      if (Character.isWhitespace(resolvedPassword.charAt(resolvedPassword.length() - 1))) {
        authWarning = String.format(message, "trailing");
      }
    }
  }

  public String getBasicAuthHeader() {
    String basicAuth = getBasicAuth();
    return basicAuth != null ? "Basic " + basicAuth : null;
  }

  /** Parsed according to http://www.ietf.org/rfc/rfc2617.txt */
  public AuthenticateDetails parseBearerAuthenticateHeader(String header) {
    final String REALM_KEY = "realm";
    final String SERVICE_KEY = "service";
    final String SCOPE_KEY = "scope";
    AuthenticateDetails result = new AuthenticateDetails();

    while (!header.isEmpty()) {
      int keyEnd = header.indexOf("=");
      if (keyEnd == -1) {
        throw new DockerRegistryAuthenticationException(
            "Www-Authenticate header terminated with junk: '" + header + "'.");
      }
      String key = header.substring(0, keyEnd);
      header = header.substring(keyEnd + 1);
      if (header.isEmpty()) {
        throw new DockerRegistryAuthenticationException(
            "Www-Authenticate header unmatched parameter key: '" + key + "'.");
      }
      String value;
      if (header.charAt(0) == '"') {
        header = header.substring(1);
        int valueEnd = header.indexOf('"');
        if (valueEnd == -1) {
          throw new DockerRegistryAuthenticationException(
              "Www-Authenticate header has unterminated \" (quotation mark).");
        }
        value = header.substring(0, valueEnd);
        header = header.substring(valueEnd + 1);
        if (!header.isEmpty()) {
          if (header.charAt(0) != ',') {
            throw new DockerRegistryAuthenticationException(
                "Www-Authenticate header params must be separated by , (comma).");
          }
          header = header.substring(1);
        }
      } else {
        int valueEnd = header.indexOf(",");
        if (valueEnd == -1) {
          value = header;
          header = "";
        } else {
          value = header.substring(0, valueEnd);
          header = header.substring(valueEnd + 1);
        }
      }
      if (key.equalsIgnoreCase(REALM_KEY)) {
        try {
          URL url = new URL(value);
          result.realm = url.getProtocol() + "://" + url.getAuthority();
          result.path = url.getPath();
          if (!result.path.isEmpty() && result.path.charAt(0) == '/') {
            result.path = result.path.substring(1);
          }
        } catch (Exception e) {
          throw new DockerRegistryAuthenticationException("Invalid realm URL: " + value, e);
        }
      } else if (key.equalsIgnoreCase(SERVICE_KEY)) {
        result.service = value;
      } else if (key.equalsIgnoreCase(SCOPE_KEY)) {
        result.scope = value;
      }
    }
    if (result.realm == null) {
      throw new DockerRegistryAuthenticationException(
          "Www-Authenticate header must provide 'realm' parameter.");
    }
    return result;
  }

  private RegistryService getTokenService(String realm) {
    RegistryService registryService = realmToService.get(realm);
    if (registryService == null) {
      registryService =
          serviceClientProvider.getService(
              RegistryService.class, new DefaultServiceEndpoint("tokenservice", realm));
      realmToService.put(realm, registryService);
    }
    return registryService;
  }

  public DockerBearerToken getToken(String repository) {
    return cachedTokens.get(repository);
  }

  public DockerBearerToken getToken(String repository, String authenticateHeader) {
    AuthenticateDetails authenticateDetails;
    try {
      authenticateDetails = parseBearerAuthenticateHeader(authenticateHeader);
    } catch (Exception e) {
      throw new DockerRegistryAuthenticationException(
          "Failed to parse www-authenticate header: " + e.getMessage());
    }
    RegistryService registryService = getTokenService(authenticateDetails.realm);
    DockerBearerToken token;
    try {
      String basicAuthHeader = getBasicAuthHeader();
      if (basicAuthHeader != null) {
        token =
            Retrofit2SyncCall.execute(
                registryService.getToken(
                    authenticateDetails.path,
                    authenticateDetails.service,
                    authenticateDetails.scope,
                    basicAuthHeader,
                    USER_AGENT));
      } else {
        token =
            Retrofit2SyncCall.execute(
                registryService.getToken(
                    authenticateDetails.path,
                    authenticateDetails.service,
                    authenticateDetails.scope,
                    USER_AGENT));
      }
    } catch (Exception e) {
      if (authWarning != null) {
        throw new DockerRegistryAuthenticationException(
            "Authentication failed (" + authWarning + "): " + e.getMessage(), e);
      } else {
        throw new DockerRegistryAuthenticationException(
            "Authentication failed: " + e.getMessage(), e);
      }
    }
    cachedTokens.put(repository, token);
    return token;
  }

  public void clearToken(String repository) {
    cachedTokens.remove(repository);
  }

  @Data
  public static class AuthenticateDetails {
    String realm;
    String path;
    String service;
    String scope;
  }
}
