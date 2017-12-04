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
package com.netflix.spinnaker.config

import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.connectionpool.impl.lb.HostToken
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dynomite")
open class DynomiteConfigurationProperties {

  var applicationName: String = "keel"
  var clusterName: String = "keel"

  var localDataCenter: String = "localrac"
  var localRack: String = "localrack"

  var hosts: MutableList<DynoHost> = mutableListOf()

  fun getDynoHosts(): MutableList<Host>
    = hosts.map { Host(it.hostname, it.ipAddress, it.port, it.rack, it.datacenter, it.status, it.hashtag) }.toMutableList()

  fun getDynoHostTokens(): MutableList<HostToken>
    = getDynoHosts().mapIndexed { index, host -> HostToken(hosts[index].token, host) }.toMutableList()
}

open class DynoHost {
  var hostname = "localhost"
  var ipAddress = "127.0.0.1"
  var port = Host.DEFAULT_PORT
  var status = Host.Status.Up
  var rack = "localrack"
  var datacenter = "localrac"
  var token = 1000000L
  var hashtag: String? = null
}
