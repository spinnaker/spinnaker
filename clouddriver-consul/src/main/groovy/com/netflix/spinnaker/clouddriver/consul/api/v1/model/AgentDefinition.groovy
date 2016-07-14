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

class AgentDefinition {
  Config Config
  Coord Coord
  Member Member

  class Config {
    Boolean Bootstrap
    Boolean Server
    String Datacenter
    String DataDir
    String DNSRecursor
    List<String> DNSRecursors
    String Domain
    String LogLevel
    String NodeName
    String ClientAddr
    String BindAddr
    String AdvertiseAddr
    Map<String, Integer> Ports
    Boolean LeaveOnTerm
    Boolean SkipLeaveOnInt
    String StatsiteAddr
    Integer Protocol
    Boolean EnableDebug
    Boolean VerifyIncoming
    Boolean VerifyOutgoing
    String CAFile
    String CertFile
    String KeyFile
    List<String> StartJoin
    String UiDir
    String PidFile
    Boolean EnableSyslog
    Boolean RejoinAfterLeave
  }

  class Coord {
    Integer Adjustment
    Integer Error
    List<Integer> Vec
  }

  class Member {
    String Name
    String Addr
    Integer Port
    Map<String, String> Tags
    Integer Status
    Integer ProtocolMin
    Integer ProtocolMax
    Integer ProtocolCur
    Integer DelegateMin
    Integer DelegateMax
    Integer DelegateCur
  }
}
