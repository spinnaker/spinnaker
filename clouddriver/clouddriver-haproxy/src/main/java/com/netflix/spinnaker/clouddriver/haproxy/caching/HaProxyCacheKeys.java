/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.haproxy.caching;

/**
 * Cache key layout: {@code haproxy;TYPE;account;region;name}. HAProxy has no native regions; the
 * region segment is the per-account configurable label.
 */
public class HaProxyCacheKeys {
  private static final String PROVIDER = "haproxy";
  private static final String SEP = ";";

  private HaProxyCacheKeys() {}

  public static String frontend(String account, String region, String name) {
    return String.join(SEP, PROVIDER, HaProxyResourceType.FRONTEND.name(), account, region, name);
  }

  public static String backend(String account, String region, String name) {
    return String.join(SEP, PROVIDER, HaProxyResourceType.BACKEND.name(), account, region, name);
  }

  public static String application(String app) {
    return String.join(SEP, PROVIDER, HaProxyResourceType.APPLICATION.name(), app);
  }

  public static String cluster(String account, String clusterName) {
    return String.join(SEP, PROVIDER, HaProxyResourceType.CLUSTER.name(), account, clusterName);
  }

  public static String globAllApplications() {
    return String.join(SEP, PROVIDER, HaProxyResourceType.APPLICATION.name(), "*");
  }

  /** Glob that matches all keys of the given cache type for a specific account. */
  public static String glob(String type, String account) {
    return String.join(SEP, PROVIDER, type, account, "*");
  }

  /** Glob that matches all keys of the given cache type across all accounts. */
  public static String globAll(String type) {
    return String.join(SEP, PROVIDER, type, "*");
  }

  public static String getAccount(String key) {
    String[] parts = key.split(SEP, -1);
    return parts.length > 2 ? parts[2] : null;
  }

  public static String getRegion(String key) {
    String[] parts = key.split(SEP, -1);
    return parts.length > 3 ? parts[3] : null;
  }

  public static String getName(String key) {
    String[] parts = key.split(SEP, -1);
    return parts.length > 4 ? parts[4] : null;
  }
}
