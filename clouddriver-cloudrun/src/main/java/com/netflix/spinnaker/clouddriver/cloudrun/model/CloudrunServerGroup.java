/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.model;

import com.google.api.services.run.v1.model.Revision;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class CloudrunServerGroup implements ServerGroup, Serializable {

  private static final String LABELS_PREFIX = "serving.knative.dev/";
  private static final String LABELS_SERVICE = LABELS_PREFIX + "service";
  private static final String LABELS_LOCATION = "cloud.googleapis.com/location";
  private final String LABELS_SERVICE_UID = LABELS_PREFIX + "serviceUid";
  private final String ANNOTATIONS_PREFIX = "autoscaling.knative.dev/";
  private final String ANNOTATIONS_MINSCALE = ANNOTATIONS_PREFIX + "minScale";
  private final String ANNOTATIONS_MAXSCALE = ANNOTATIONS_PREFIX + "maxScale";
  private String name;
  private final String type = CloudrunCloudProvider.ID;
  private final String cloudProvider = CloudrunCloudProvider.ID;
  private String account;
  private String region;
  private final Set<String> zones = ImmutableSet.of();
  private Set<CloudrunInstance> instances;
  private Set<String> loadBalancers = new HashSet<>();
  private Long createdTime;
  private final Map<String, Object> launchConfig = ImmutableMap.of();
  private final Set<String> securityGroups = ImmutableSet.of();
  private Boolean disabled = true;
  private ServingStatus servingStatus;
  private String instanceClass;
  private Integer minTotalInstances;
  private Integer maxTotalInstances;
  private String serviceName;
  private String namespace;

  private Map<String, Object> tags = new HashMap<>();

  public CloudrunServerGroup() {}

  public CloudrunServerGroup(Revision revision, String account, String loadBalancerName) {
    this.account = account;
    this.region = getRegion(revision);
    this.name = revision.getMetadata().getName();
    this.loadBalancers.add(loadBalancerName);
    this.createdTime =
        CloudrunModelUtil.translateTime(revision.getMetadata().getCreationTimestamp());
    this.disabled = isDisabled(revision);
    this.servingStatus = this.disabled ? ServingStatus.SERVING : ServingStatus.STOPPED;
    this.minTotalInstances = getMinTotalInstances(revision);
    this.maxTotalInstances = getMaxTotalInstances(revision);
    this.serviceName = getServiceName(revision);
  }

  @Override
  public InstanceCounts getInstanceCounts() {
    InstanceCounts counts = new InstanceCounts();
    return counts
        .setDown(0)
        .setOutOfService(0)
        .setUp(minTotalInstances)
        .setStarting(0)
        .setUnknown(0)
        .setTotal(minTotalInstances);
  }

  @Override
  public Capacity getCapacity() {
    Capacity capacity = new Capacity();
    return capacity
        .setMin(minTotalInstances)
        .setMax(maxTotalInstances)
        .setDesired(minTotalInstances);
  }

  @Override
  public Map<String, Object> getTags() {
    return tags;
  }

  private Integer getMinTotalInstances(Revision revision) {
    String minScale = revision.getMetadata().getAnnotations().get(ANNOTATIONS_MINSCALE);
    if (minScale == null) { // only when minscale > 0, Revision yaml will have minscale annotation
      return 0;
    }
    return Integer.parseInt(minScale);
  }

  private Integer getMaxTotalInstances(Revision revision) {
    return Integer.parseInt(revision.getMetadata().getAnnotations().get(ANNOTATIONS_MAXSCALE));
  }

  public static String getServiceName(Revision revision) {
    return revision.getMetadata().getLabels().get(LABELS_SERVICE);
  }

  public static String getLocationLabel() {
    return LABELS_LOCATION;
  }

  public static String getRegion(Revision revision) {
    return revision.getMetadata().getLabels().get(LABELS_LOCATION);
  }

  @Override
  public ImageSummary getImageSummary() {
    return null;
  }

  @Override
  public ImagesSummary getImagesSummary() {
    return null;
  }

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  public Boolean isDisabled(Revision revision) {
    try {
      String activeConditionStatus =
          revision.getStatus().getConditions().stream()
              .filter(s -> s.getType().equalsIgnoreCase("Active"))
              .collect(Collectors.toList())
              .get(0)
              .getStatus();
      return !activeConditionStatus.equals("True");
    } catch (IndexOutOfBoundsException e) {
      log.error("No conditions exist on the Revision!! {}", e.getMessage());
      return true;
    }
  }

  public static enum ServingStatus {
    SERVING,
    STOPPED;
  }
}
