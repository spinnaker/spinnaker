/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to map masters in properties file into a validated property map
 */
@Data
@ConfigurationProperties(prefix = "jenkins")
@Validated
public class JenkinsProperties implements BuildServerProperties<JenkinsProperties.JenkinsHost> {
  @Valid
  private List<JenkinsHost> masters;

  @Data
  public static class JenkinsHost implements BuildServerProperties.Host {
    @NotEmpty
    private String name;

    @NotEmpty
    private String address;

    private String username;
    private String password;
    private Boolean csrf = false;

    // These are needed for Google-based OAuth with a service account credential
    private String jsonPath;
    private List<String> oauthScopes = new ArrayList<>();

    // Can be used directly, if available.
    private String token;

    private Integer itemUpperThreshold;

    private String trustStore;
    private String trustStoreType = KeyStore.getDefaultType();
    private String trustStorePassword;

    private String keyStore;
    private String keyStoreType = KeyStore.getDefaultType();
    private String keyStorePassword;

    private Boolean skipHostnameVerification = false;
    private Boolean ciEnabled = false;

    private Permissions.Builder permissions = new Permissions.Builder();
  }
}
