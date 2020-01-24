/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.dynomite;

import static com.netflix.dyno.connectionpool.Host.DEFAULT_DATASTORE_PORT;
import static com.netflix.dyno.connectionpool.Host.DEFAULT_PORT;
import static com.netflix.dyno.connectionpool.Host.Status;

import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.connectionpool.impl.lb.HostToken;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DynomiteDriverProperties {

  private static final String LOCAL_RACK = "localrack";
  private static final String LOCAL_DATACENTER = "localrac";

  public String applicationName;
  public String clusterName;

  public String localRack = LOCAL_RACK;
  public String localDatacenter = LOCAL_DATACENTER;

  public boolean forceUseStaticHosts = false;
  public List<DynoHost> hosts = new ArrayList<>();

  public ConnectionPoolConfigurationImpl connectionPool;

  public List<Host> getDynoHosts() {
    return hosts.stream()
        .map(
            it ->
                new Host(
                    it.hostname,
                    it.ipAddress,
                    it.port,
                    it.securePort,
                    it.datastorePort,
                    it.rack,
                    it.datacenter,
                    it.status,
                    it.hashtag,
                    it.password))
        .collect(Collectors.toList());
  }

  public List<HostToken> getDynoHostTokens() {
    List<HostToken> tokens = new ArrayList<>();
    List<Host> dynoHosts = getDynoHosts();
    for (int i = 0; i < dynoHosts.size(); i++) {
      tokens.add(new HostToken(hosts.get(i).token, dynoHosts.get(i)));
    }
    return tokens;
  }

  static class DynoHost {
    public String hostname;
    public String ipAddress;
    public int port = DEFAULT_PORT;
    public int securePort = DEFAULT_PORT;
    public int datastorePort = DEFAULT_DATASTORE_PORT;
    public Status status = Status.Up;
    public String rack = LOCAL_RACK;
    public String datacenter = LOCAL_DATACENTER;
    public Long token = 1000000L;
    public String hashtag = "{}";
    public String password;
  }
}
