/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.consul.api.v1.model

import com.fasterxml.jackson.annotation.JsonProperty

class AgentDefinition {
  @JsonProperty("Config")
  Config config

  @JsonProperty("Coord")
  Coord coord

  @JsonProperty("Member")
  Member member


  class Config {
    @JsonProperty("Bootstrap")
    Boolean bootstrap

    @JsonProperty("Server")
    Boolean server

    @JsonProperty("Datacenter")
    String datacenter

    @JsonProperty("DataDir")
    String dataDir

    @JsonProperty("DNSRecursor")
    String dnsRecursor

    @JsonProperty("DNSRecursors")
    List<String> dnsRecursors

    @JsonProperty("Domain")
    String domain

    @JsonProperty("LogLevel")
    String logLevel

    @JsonProperty("NodeName")
    String nodeName

    @JsonProperty("ClientAddr")
    String clientAddr

    @JsonProperty("BindAddr")
    String bindAddr

    @JsonProperty("AdvertiseAddr")
    String advertiseAddr

    @JsonProperty("Ports")
    Map<String, Integer> ports

    @JsonProperty("LeaveOnTerm")
    Boolean leaveOnTerm

    @JsonProperty("SkipLeaveOnInt")
    Boolean skipLeaveOnInt

    @JsonProperty("StatsiteAddr")
    String statsiteAddr

    @JsonProperty("Protocol")
    Integer protocol

    @JsonProperty("EnableDebug")
    Boolean enableDebug

    @JsonProperty("VerifyIncoming")
    Boolean verifyIncoming

    @JsonProperty("VerifyOutgoing")
    Boolean verifyOutgoing

    @JsonProperty("CAFile")
    String caFile

    @JsonProperty("CertFile")
    String certFile

    @JsonProperty("KeyFile")
    String keyFile

    List<String> StartJoin
    @JsonProperty("UiDir")
    String uiDir

    @JsonProperty("PidFile")
    String pidFile

    @JsonProperty("EnableSyslog")
    Boolean enableSyslog

    @JsonProperty("RejoinAfterLeave")
    Boolean rejoinAfterLeave

  }

  class Coord {
    @JsonProperty("Adjustment")
    Integer adjustment

    @JsonProperty("Error")
    Integer error

    @JsonProperty("Vec")
    List<Integer> vec
  }

  class Member {
    @JsonProperty("Name")
    String name

    @JsonProperty("Addr")
    String addr

    @JsonProperty("Port")
    Integer port

    @JsonProperty("Tags")
    Map<String, String> tags

    @JsonProperty("Status")
    Integer status

    @JsonProperty("ProtocolMin")
    Integer protocolMin

    @JsonProperty("ProtocolMax")
    Integer protocolMax

    @JsonProperty("ProtocolCur")
    Integer protocolCur

    @JsonProperty("DelegateMin")
    Integer delegateMin

    @JsonProperty("DelegateMax")
    Integer delegateMax

    @JsonProperty("DelegateCur")
    Integer delegateCur

  }
}
