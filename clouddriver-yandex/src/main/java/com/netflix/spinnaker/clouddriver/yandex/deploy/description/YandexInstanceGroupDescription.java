/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.description;

import com.google.common.base.Strings;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.*;
import lombok.*;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class YandexInstanceGroupDescription
    implements CredentialsChangeable, Cloneable, DeployDescription, ApplicationNameable {
  private YandexCloudCredentials credentials;

  private String application;
  private String stack;
  private String freeFormDetails;

  private String sourceServerGroupName;

  private String name;
  private String description;
  private Set<String> zones;
  private Map<String, String> labels;
  private Long targetSize;
  private YandexCloudServerGroup.AutoScalePolicy autoScalePolicy;
  private YandexCloudServerGroup.DeployPolicy deployPolicy;
  private YandexCloudServerGroup.TargetGroupSpec targetGroupSpec;
  private List<YandexCloudServerGroup.HealthCheckSpec> healthCheckSpecs;
  private YandexCloudServerGroup.InstanceTemplate instanceTemplate;
  private String serviceAccountId;
  private Map<String, List<YandexCloudServerGroup.HealthCheckSpec>> balancers;
  private Boolean enableTraffic;

  private Source source;

  @Override
  public Collection<String> getApplications() {
    if (!Strings.isNullOrEmpty(application)) {
      return Collections.singletonList(application);
    }

    if (!Strings.isNullOrEmpty(getName())) {
      return Collections.singletonList(Names.parseName(getName()).getApp());
    }

    return null;
  }

  public void produceServerGroupName() {
    YandexServerGroupNameResolver serverGroupNameResolver =
        new YandexServerGroupNameResolver(getCredentials());
    this.setName(
        serverGroupNameResolver.resolveNextServerGroupName(
            getApplication(), getStack(), getFreeFormDetails(), false));
  }

  public void saturateLabels() {
    if (getLabels() == null) {
      setLabels(new HashMap<>());
    }
    if (getInstanceTemplate().getLabels() == null) {
      getInstanceTemplate().setLabels(new HashMap<>());
    }

    Integer sequence = Names.parseName(getName()).getSequence();
    String clusterName =
        new YandexServerGroupNameResolver(getCredentials())
            .combineAppStackDetail(getApplication(), getStack(), getFreeFormDetails());

    saturateLabels(getLabels(), sequence, clusterName);
    saturateLabels(getInstanceTemplate().getLabels(), sequence, clusterName);
  }

  private void saturateLabels(Map<String, String> labels, Integer sequence, String clusterName) {
    labels.putIfAbsent("spinnaker-server-group", this.getName());
    labels.putIfAbsent("spinnaker-moniker-application", this.getApplication());
    labels.putIfAbsent("spinnaker-moniker-cluster", clusterName);
    labels.putIfAbsent("spinnaker-moniker-stack", this.getStack());
    labels.put("spinnaker-moniker-sequence", sequence == null ? null : sequence.toString());
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class Source {
    String serverGroupName;
    Boolean useSourceCapacity;
  }
}
