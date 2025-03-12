/*
 * Copyright 2019 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.alicloud.model.alienum.ListenerType;
import lombok.Data;

@Data
public class Listener {

  ListenerType listenerProtocal;

  private String healthCheck = "on";

  private Integer healthCheckTimeout = 5;

  private Integer unhealthyThreshold = 3;

  private Integer healthyThreshold = 3;

  private Integer healthCheckInterval = 2;

  private Long resourceOwnerId;

  private String listenerForward;

  @JsonProperty("xForwardedFor")
  private String xForwardedFor;

  private String healthCheckURI;

  private String description;

  private String aclStatus;

  private String scheduler;

  private String aclType;

  private Integer forwardPort;

  private Integer cookieTimeout;

  private String stickySessionType;

  @JsonProperty("vServerGroupId")
  private String vServerGroupId;

  private String aclId;

  private Integer listenerPort;

  private String cookie;

  private String resourceOwnerAccount;

  private Integer bandwidth;

  private String stickySession;

  private String healthCheckDomain;

  private Integer requestTimeout;

  private String ownerAccount;

  private String gzip;

  private Long ownerId;

  private Integer idleTimeout;

  private String loadBalancerId;

  @JsonProperty("xForwardedFor_SLBIP")
  private String xForwardedFor_SLBIP;

  private Integer backendServerPort;

  @JsonProperty("xForwardedFor_proto")
  private String xForwardedFor_proto;

  @JsonProperty("xForwardedFor_SLBID")
  private String xForwardedFor_SLBID;

  private Integer healthCheckConnectPort;

  private String healthCheckHttpCode;

  private String enableHttp2;

  @JsonProperty("tLSCipherPolicy")
  private String tLSCipherPolicy;

  private String serverCertificateId;

  @JsonProperty("cACertificateId")
  private String cACertificateId;

  private Integer healthCheckConnectTimeout;

  private Integer establishedTimeout;

  private Integer persistenceTimeout;

  private String healthCheckType;

  private String masterSlaveServerGroupId;

  private String healthCheckReq;

  private String healthCheckExp;
}
