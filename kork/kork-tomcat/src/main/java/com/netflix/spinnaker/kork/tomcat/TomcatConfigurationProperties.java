/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.kork.tomcat;

import com.netflix.spinnaker.kork.crypto.CipherSuites;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("default")
public class TomcatConfigurationProperties {
  private int legacyServerPort = -1;

  private int apiPort = -1;

  private String relaxedQueryCharacters = "";
  private String relaxedPathCharacters = "";

  private List<String> tlsVersions = new ArrayList<>(Arrays.asList("TLSv1.3", "TLSv1.2"));

  private List<String> cipherSuites = CipherSuites.getRecommendedCiphers();

  // This property maps to spring boot property server.tomcat.reject-illegal-header,
  // which is true by default.
  private Boolean rejectIllegalHeader;

  public int getLegacyServerPort() {
    return legacyServerPort;
  }

  public void setLegacyServerPort(int legacyServerPort) {
    this.legacyServerPort = legacyServerPort;
  }

  public int getApiPort() {
    return apiPort;
  }

  public void setApiPort(int apiPort) {
    this.apiPort = apiPort;
  }

  public String getRelaxedQueryCharacters() {
    return relaxedQueryCharacters;
  }

  public void setRelaxedQueryCharacters(String relaxedQueryCharacters) {
    this.relaxedQueryCharacters = relaxedQueryCharacters;
  }

  public String getRelaxedPathCharacters() {
    return relaxedPathCharacters;
  }

  public void setRelaxedPathCharacters(String relaxedPathCharacters) {
    this.relaxedPathCharacters = relaxedPathCharacters;
  }

  public List<String> getTlsVersions() {
    return tlsVersions;
  }

  public void setTlsVersions(List<String> tlsVersions) {
    this.tlsVersions = tlsVersions;
  }

  public List<String> getCipherSuites() {
    return cipherSuites;
  }

  public void setCipherSuites(List<String> cipherSuites) {
    this.cipherSuites = cipherSuites;
  }

  public Boolean getRejectIllegalHeader() {
    return rejectIllegalHeader;
  }

  public void setRejectIllegalHeader(Boolean rejectIllegalHeader) {
    this.rejectIllegalHeader = rejectIllegalHeader;
  }
}
