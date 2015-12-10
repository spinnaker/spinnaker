/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kork.astyanax;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.astyanax.connectionpool.Host;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class EurekaHostSupplier implements Supplier<List<Host>> {
  public static class Factory implements ClusterHostSupplierFactory {
    private final DiscoveryClient discoveryClient;

    public Factory(DiscoveryClient discoveryClient) {
      this.discoveryClient = Preconditions.checkNotNull(discoveryClient, "discoveryClient");
    }

    @Override
    public Supplier<List<Host>> createHostSupplier(String clusterName) {
      return new EurekaHostSupplier(clusterName, discoveryClient);
    }
  }

  public static ClusterHostSupplierFactory factory(DiscoveryClient discoveryClient) {
    return new Factory(discoveryClient);
  }

  private static final Logger logger = LoggerFactory.getLogger(EurekaHostSupplier.class);
  private final String applicationName;
  private final DiscoveryClient discoveryClient;

  public EurekaHostSupplier(String applicationName, DiscoveryClient discoveryClient) {
    this.applicationName = Preconditions.checkNotNull(applicationName, "applicationName");
    this.discoveryClient = Preconditions.checkNotNull(discoveryClient, "discoveryClient");
  }

  @Override
  public List<Host> get() {
    return Optional.ofNullable(discoveryClient.getApplication(applicationName))
      .map(Application::getInstances)
      .map(List::stream)
      .map(ss ->
        ss.filter(EurekaHostSupplier::isUp)
          .map(EurekaHostSupplier::buildHost)
          .collect(Collectors.toList()))
      .orElse(Collections.emptyList());
  }

  static boolean isUp(InstanceInfo info) {
    return info.getStatus() == InstanceInfo.InstanceStatus.UP;
  }

  static Host buildHost(InstanceInfo info) {
    String[] parts = StringUtils.split(
      StringUtils.split(info.getHostName(), ".")[0], '-');

    Host host = new Host(info.getHostName(), info.getPort())
      .addAlternateIpAddress(
        StringUtils.join(new String[] { parts[1], parts[2], parts[3],
          parts[4] }, "."))
      .addAlternateIpAddress(info.getIPAddr())
      .setId(info.getId());

    try {
      if (info.getDataCenterInfo() instanceof AmazonInfo) {
        AmazonInfo amazonInfo = (AmazonInfo)info.getDataCenterInfo();
        host.setRack(amazonInfo.get(AmazonInfo.MetaDataKey.availabilityZone));
      }
    }
    catch (Throwable t) {
      logger.error("Error getting rack for host " + host.getName(), t);
    }

    return host;
  }
}
