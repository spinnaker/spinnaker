/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;

import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider.ID;

public class Keys implements KeyParser {
  public enum Namespace {
    IAM_ROLE,
    SERVICES,
    ECS_CLUSTERS,
    TASKS,
    CONTAINER_INSTANCES,
    TASK_DEFINITIONS,
    ALARMS,
    SCALABLE_TARGETS,
    SECRETS,
    SERVICE_DISCOVERY_REGISTRIES;

    public final String ns;

    Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    public String toString() {
      return ns;
    }
  }

  public static final String SEPARATOR = ";";

  @Override
  public String getCloudProvider() {
    return ID;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  @Override
  public Boolean canParseType(String type) {
    return canParse(type);
  }

  private static Boolean canParse(String type) {
    for (Namespace key : Namespace.values()) {
      if (key.toString().equals(type)) {
        return true;
      }
    }
    return false;
  }

  public static Map<String, String> parse(String key) {
    String[] parts = key.split(SEPARATOR);

    if (parts.length < 3 || !parts[0].equals(ID)) {
      return null;
    }

    Map<String, String> result = new HashMap<>();
    result.put("provider", parts[0]);
    result.put("type", parts[1]);
    result.put("account", parts[2]);

    if(!canParse(parts[1]) && parts[1].equals(HEALTH.getNs())){
      result.put("region", parts[3]);
      result.put("taskId", parts[4]);
      return result;
    }


    Namespace namespace = Namespace.valueOf(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, parts[1]));

    if (!namespace.equals(Namespace.IAM_ROLE)) {
      result.put("region", parts[3]);
    }

    switch (namespace) {
      case SERVICES:
        result.put("serviceName", parts[4]);
        break;
      case ECS_CLUSTERS:
        result.put("clusterName", parts[4]);
        break;
      case TASKS:
        result.put("taskId", parts[4]);
        break;
      case CONTAINER_INSTANCES:
        result.put("containerInstanceArn", parts[4]);
        break;
      case TASK_DEFINITIONS:
        result.put("taskDefinitionArn", parts[4]);
        break;
      case ALARMS:
        result.put("alarmArn", parts[4]);
        break;
      case IAM_ROLE:
        result.put("roleName", parts[3]);
        break;
      case SECRETS:
        result.put("secretName", parts[4]);
        break;
      case SERVICE_DISCOVERY_REGISTRIES:
        result.put("serviceId", parts[4]);
        break;
      case SCALABLE_TARGETS:
        result.put("resource", parts[4]);
        break;
      default:
        break;
    }

    return result;
  }

  @Override
  public Boolean canParseField(String type) {
    return false;
  }

  public static String getServiceKey(String account, String region, String serviceName) {
    return buildKey(Namespace.SERVICES.ns, account, region, serviceName);
  }

  public static String getClusterKey(String account, String region, String clusterName) {
    return buildKey(Namespace.ECS_CLUSTERS.ns, account, region, clusterName);
  }

  public static String getTaskKey(String account, String region, String taskId) {
    return buildKey(Namespace.TASKS.ns, account, region, taskId);
  }

  public static String getTaskHealthKey(String account, String region, String taskId) {
    return buildKey(HEALTH.getNs(), account, region, taskId);
  }

  public static String getContainerInstanceKey(String account, String region, String containerInstanceArn) {
    return buildKey(Namespace.CONTAINER_INSTANCES.ns, account, region, containerInstanceArn);
  }

  public static String getTaskDefinitionKey(String account, String region, String taskDefinitionArn) {
    return buildKey(Namespace.TASK_DEFINITIONS.ns, account, region, taskDefinitionArn);
  }

  public static String getAlarmKey(String account, String region, String alarmArn) {
    return buildKey(Namespace.ALARMS.ns, account, region, alarmArn);
  }

  public static String getScalableTargetKey(String account, String region, String resourceId) {
    return buildKey(Namespace.SCALABLE_TARGETS.ns, account, region, resourceId);
  }

  public static String getIamRoleKey(String account, String iamRoleName) {
    return ID + SEPARATOR + Namespace.IAM_ROLE + SEPARATOR + account + SEPARATOR + iamRoleName;
  }

  public static String getSecretKey(String account, String region, String secretName) {
    return buildKey(Namespace.SECRETS.ns, account, region, secretName);
  }

  public static String getServiceDiscoveryRegistryKey(String account, String region, String registryId) {
    return buildKey(Namespace.SERVICE_DISCOVERY_REGISTRIES.ns, account, region, registryId);
  }

  private static String buildKey(String namespace,String account, String region, String identifier){
    return ID + SEPARATOR + namespace + SEPARATOR + account + SEPARATOR + region + SEPARATOR + identifier;
  }
}
