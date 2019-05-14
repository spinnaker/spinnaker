/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import java.util.List;

public class UpsertAmazonLoadBalancerClassicDescription
    extends UpsertAmazonLoadBalancerDescription {
  private List<Listener> listeners;
  private String healthCheck;
  private Integer healthCheckPort;
  private Integer healthInterval = 10;
  private Integer healthTimeout = 5;
  private Integer unhealthyThreshold = 2;
  private Integer healthyThreshold = 10;
  private String application;
  private Boolean connectionDraining;
  private Integer deregistrationDelay;

  public List<Listener> getListeners() {
    return listeners;
  }

  public void setListeners(List<Listener> listeners) {
    this.listeners = listeners;
  }

  public String getHealthCheck() {
    return healthCheck;
  }

  public void setHealthCheck(String healthCheck) {
    this.healthCheck = healthCheck;
  }

  public Integer getHealthInterval() {
    return healthInterval;
  }

  public void setHealthInterval(Integer healthInterval) {
    this.healthInterval = healthInterval;
  }

  public Integer getHealthTimeout() {
    return healthTimeout;
  }

  public void setHealthTimeout(Integer healthTimeout) {
    this.healthTimeout = healthTimeout;
  }

  public Integer getUnhealthyThreshold() {
    return unhealthyThreshold;
  }

  public void setUnhealthyThreshold(Integer unhealthyThreshold) {
    this.unhealthyThreshold = unhealthyThreshold;
  }

  public Integer getHealthyThreshold() {
    return healthyThreshold;
  }

  public void setHealthyThreshold(Integer healthyThreshold) {
    this.healthyThreshold = healthyThreshold;
  }

  public Boolean getCrossZoneBalancing() {
    return crossZoneBalancing;
  }

  public void setCrossZoneBalancing(Boolean crossZoneBalancing) {
    this.crossZoneBalancing = crossZoneBalancing;
  }

  private Boolean crossZoneBalancing;

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public Integer getHealthCheckPort() {
    return healthCheckPort;
  }

  public void setHealthCheckPort(Integer healthCheckPort) {
    this.healthCheckPort = healthCheckPort;
  }

  public Boolean getConnectionDraining() {
    return connectionDraining;
  }

  public void setConnectionDraining(Boolean connectionDraining) {
    this.connectionDraining = connectionDraining;
  }

  public Integer getDeregistrationDelay() {
    return deregistrationDelay;
  }

  public void setDeregistrationDelay(Integer deregistrationDelay) {
    this.deregistrationDelay = deregistrationDelay;
  }

  public static class Listener {
    public enum ListenerType {
      HTTP,
      HTTPS,
      TCP,
      SSL
    }

    private ListenerType externalProtocol;
    private ListenerType internalProtocol;

    private Integer externalPort;
    private Integer internalPort;

    private String sslCertificateId;

    public ListenerType getExternalProtocol() {
      return externalProtocol;
    }

    public void setExternalProtocol(ListenerType externalProtocol) {
      this.externalProtocol = externalProtocol;
    }

    public ListenerType getInternalProtocol() {
      return internalProtocol;
    }

    public void setInternalProtocol(ListenerType internalProtocol) {
      this.internalProtocol = internalProtocol;
    }

    public Integer getExternalPort() {
      return externalPort;
    }

    public void setExternalPort(Integer externalPort) {
      this.externalPort = externalPort;
    }

    public Integer getInternalPort() {
      return internalPort;
    }

    public void setInternalPort(Integer internalPort) {
      this.internalPort = internalPort;
    }

    public String getSslCertificateId() {
      return sslCertificateId;
    }

    public void setSslCertificateId(String sslCertificateId) {
      this.sslCertificateId = sslCertificateId;
    }
  }
}
