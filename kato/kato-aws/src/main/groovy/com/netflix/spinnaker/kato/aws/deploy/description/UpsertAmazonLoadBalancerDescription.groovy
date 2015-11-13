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

package com.netflix.spinnaker.kato.aws.deploy.description

class UpsertAmazonLoadBalancerDescription extends AbstractAmazonCredentialsDescription {
  String clusterName
  String name
  String vpcId
  String subnetType
  List<String> securityGroups
  Map<String, List<String>> availabilityZones
  List<Listener> listeners
  String healthCheck
  Integer healthInterval = 10
  Integer healthTimeout = 5
  Integer unhealthyThreshold = 2
  Integer healthyThreshold = 10
  Boolean crossZoneBalancing = Boolean.TRUE

  static class Listener {
    enum ListenerType {
      HTTP, HTTPS, TCP, SSL
    }

    ListenerType externalProtocol
    ListenerType internalProtocol

    Integer externalPort
    Integer internalPort

    String sslCertificateId
  }
}
