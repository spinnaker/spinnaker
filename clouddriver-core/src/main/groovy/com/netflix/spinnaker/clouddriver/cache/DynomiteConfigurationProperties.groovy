/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.cache

import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.connectionpool.Host.Status
import com.netflix.dyno.connectionpool.impl.lb.HostToken
import org.springframework.boot.context.properties.ConfigurationProperties

import javax.validation.constraints.NotNull

/**
 * Static host setup defines defaults for running Dynomite locally. Discovery should be used for any
 * non-development environment.
 */
@ConfigurationProperties("dynomite")
class DynomiteConfigurationProperties {

  Boolean enabled = false

  String applicationName = "clouddriver"
  String clusterName = "dyno_clouddriver"

  String localRack = "localrack"
  String localDataCenter = "localrac"

  List<DynoHost> hosts = []

  static class DynoHost {
    @NotNull
    String hostname

    String ipAddress

    int port = Host.DEFAULT_PORT

    Status status = Status.Up

    @NotNull
    String rack = 'localrack'

    @NotNull
    String datacenter = 'localrac'

    Long token = 1000000L
  }

  List<Host> getDynoHosts() {
    return hosts.collect { new Host(it.hostname, it.ipAddress, it.port, it.rack, it.datacenter, it.status) }
  }

  List<HostToken> getDynoHostTokens() {
    List<HostToken> tokens = []
    getDynoHosts().eachWithIndex { v, i ->
      tokens.add(new HostToken(hosts.get(i).token, v))
    }
    return tokens
  }
}
