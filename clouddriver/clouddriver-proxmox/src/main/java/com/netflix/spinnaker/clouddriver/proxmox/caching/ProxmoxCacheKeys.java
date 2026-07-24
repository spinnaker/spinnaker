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
package com.netflix.spinnaker.clouddriver.proxmox.caching;

public class ProxmoxCacheKeys {
  private static final String PROVIDER = "proxmox";
  private static final String SEP = ";";

  private ProxmoxCacheKeys() {}

  public static String vm(String account, String node, int vmid) {
    return String.join(
        SEP, PROVIDER, ProxmoxResourceType.VM.name(), account, node, String.valueOf(vmid));
  }

  public static String lxc(String account, String node, int vmid) {
    return String.join(
        SEP, PROVIDER, ProxmoxResourceType.CONTAINER.name(), account, node, String.valueOf(vmid));
  }

  public static String node(String account, String node) {
    return String.join(SEP, PROVIDER, ProxmoxResourceType.NODE.name(), account, node);
  }

  public static String storage(String account, String node, String storage) {
    return String.join(SEP, PROVIDER, ProxmoxResourceType.STORAGE.name(), account, node, storage);
  }

  public static String application(String app) {
    return String.join(SEP, PROVIDER, ProxmoxResourceType.APPLICATION.name(), app);
  }

  public static String cluster(String account, String clusterName) {
    return String.join(SEP, PROVIDER, ProxmoxResourceType.CLUSTER.name(), account, clusterName);
  }

  public static String image(String account, String node, int vmid) {
    return String.join(
        SEP, PROVIDER, ProxmoxResourceType.IMAGE.name(), account, node, String.valueOf(vmid));
  }

  public static String globAllApplications() {
    return String.join(SEP, PROVIDER, ProxmoxResourceType.APPLICATION.name(), "*");
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

  public static String getNode(String key) {
    String[] parts = key.split(SEP, -1);
    return parts.length > 3 ? parts[3] : null;
  }

  public static String getId(String key) {
    String[] parts = key.split(SEP, -1);
    return parts.length > 4 ? parts[4] : null;
  }
}
