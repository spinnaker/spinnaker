/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kork.aws.bastion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bastion")
public class BastionProperties {
  private Boolean enabled;
  private String host;
  private String user;
  private Integer port;
  private String proxyCluster;
  private String proxyRegion;
  private String accountIamRole;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  public String getProxyCluster() {
    return proxyCluster;
  }

  public void setProxyCluster(String proxyCluster) {
    this.proxyCluster = proxyCluster;
  }

  public String getProxyRegion() {
    return proxyRegion;
  }

  public void setProxyRegion(String proxyRegion) {
    this.proxyRegion = proxyRegion;
  }

  public String getAccountIamRole() {
    return accountIamRole;
  }

  public void setAccountIamRole(String accountIamRole) {
    this.accountIamRole = accountIamRole;
  }
}
