/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.cache;

import com.google.common.base.CaseFormat;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import groovy.util.logging.Slf4j;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Getter;

@Slf4j
public class Keys {

  public static final String KEY_DELIMITER = ":";

  public enum Namespace {
    APPLICATIONS,
    PLATFORM_APPLICATIONS,
    CLUSTERS,
    SERVER_GROUPS,
    INSTANCES,
    LOAD_BALANCERS,
    ON_DEMAND;

    public static String provider = CloudrunCloudProvider.ID;

    @Getter final String ns;

    private Namespace() {
      this.ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name()); // FOO_BAR -> fooBar
    }

    public String toString() {
      return ns;
    }

    public static Namespace from(String ns) {
      return Stream.of(values())
          .filter(namespace -> namespace.ns.equals(ns))
          .findAny()
          .orElseThrow(IllegalArgumentException::new);
    }
  }

  public static Map<String, String> parse(String key) {
    String[] parts = key.split(":");

    if (parts.length < 2 || !parts[0].equals(CloudrunCloudProvider.ID)) {
      return null;
    }
    Map<String, String> result = new HashMap<>();
    result.put("provider", parts[0]);
    result.put("type", parts[1]);
    Namespace namespace = Namespace.from(parts[1]);
    switch (namespace) {
      case APPLICATIONS:
        result.put("application", parts[2]);
        break;
      case PLATFORM_APPLICATIONS:
        result.put("project", parts[2]);
        break;
      case CLUSTERS:
        Names names = Names.parseName(parts[4]);
        result.put("account", parts[2]);
        result.put("application", parts[3]);
        result.put("name", parts[4]);
        result.put("cluster", parts[4]);
        break;
      case INSTANCES:
        result.put("account", parts[2]);
        result.put("name", parts[3]);
        result.put("instance", parts[3]);
        break;
      case LOAD_BALANCERS:
        result.put("account", parts[2]);
        result.put("name", parts[3]);
        result.put("loadBalancer", parts[3]);
        break;
      case SERVER_GROUPS:
        Names names_1 = Names.parseName(parts[5]);
        result.put("application", names_1.getApp());
        result.put("cluster", parts[2]);
        result.put("account", parts[3]);
        result.put("region", parts[4]);
        result.put("serverGroup", parts[5]);
        result.put("name", parts[5]);
        break;
      default:
        break;
    }
    return result;
  }

  public static String getApplicationKey(String application) {
    return keyFor(Namespace.APPLICATIONS, application);
  }

  public static String getPlatformApplicationKey(String project) {
    return keyFor(Namespace.PLATFORM_APPLICATIONS, project);
  }

  public static String getClusterKey(String account, String application, String clusterName) {
    return keyFor(Namespace.CLUSTERS, account, application, clusterName);
  }

  public static String getInstanceKey(String account, String instanceName) {
    return keyFor(Namespace.INSTANCES, account, instanceName);
  }

  public static String getLoadBalancerKey(String account, String loadBalancerName) {
    return keyFor(Namespace.LOAD_BALANCERS, account, loadBalancerName);
  }

  public static String getServerGroupKey(String account, String serverGroupName, String region) {
    Names names = Names.parseName(serverGroupName);
    return keyFor(Namespace.SERVER_GROUPS, names.getCluster(), account, region, names.getGroup());
  }

  private static String keyFor(Namespace namespace, String... parts) {
    StringBuilder builder =
        new StringBuilder(CloudrunCloudProvider.ID + KEY_DELIMITER).append(namespace);
    for (String part : parts) {
      builder.append(KEY_DELIMITER).append(part);
    }
    return builder.toString();
  }
}
