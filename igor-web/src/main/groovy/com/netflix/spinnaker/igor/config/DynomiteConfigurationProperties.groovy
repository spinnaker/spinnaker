/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.igor.config

import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.connectionpool.impl.lb.HostToken
import org.springframework.boot.context.properties.ConfigurationProperties

import javax.validation.constraints.NotNull

@ConfigurationProperties("dynomite")
class DynomiteConfigurationProperties {

    String applicationName = "igor"
    String clusterName = "dyno_igor"

    String localRack = "localrack"
    String localDataCenter = "localrac"

    List<DynoHost> hosts = []

    static class DynoHost {
        @NotNull
        String hostname

        String ipAddress

        int port = Host.DEFAULT_PORT

        Host.Status status = Host.Status.Up

        @NotNull
        String rack = 'localrack'

        @NotNull
        String datacenter = 'localrac'

        Long token = 1000000L

        String hashtag
    }

    List<Host> getDynoHosts() {
        return hosts.collect { new Host(it.hostname, it.ipAddress, it.port, it.rack, it.datacenter, it.status, it.hashtag) }
    }

    List<HostToken> getDynoHostTokens() {
        List<HostToken> tokens = []
        getDynoHosts().eachWithIndex { v, i ->
            tokens.add(new HostToken(hosts.get(i).token, v))
        }
        return tokens
    }
}
