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

package com.netflix.spinnaker.clouddriver.yandex.model;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YandexCloudServerGroup implements ServerGroup {
  // We store information about load balancers where server group attached to in
  // instanceTemplate.metadata.
  // Format is 'lb-name1=hc-spec;lb-name2=hc-spec;...'
  // Format hc-spec is 'protocol,port,path,interval,timeout,unhealthyThreshold,healthyThreshold'
  // Format for multiple hc-spec is 'hc-spec&hc-spec&hc-spec'
  public static final String LOAD_BALANCERS_SPECS = "load-balancer-names";

  private String id;
  private String folder;
  private String name;
  private String type = YandexCloudProvider.ID;
  private String cloudProvider = YandexCloudProvider.ID;
  private String region;
  private Boolean disabled;
  private Long createdTime;
  private Set<String> zones;
  private Set<YandexCloudInstance> instances;
  private Set<String> securityGroups;
  private Map<String, Object> launchConfig;
  private InstanceCounts instanceCounts;
  private Capacity capacity;
  private ImageSummary imageSummary;
  private ImagesSummary imagesSummary;
  private Map<String, String> labels;
  private String description;
  private AutoScalePolicy autoScalePolicy;
  private DeployPolicy deployPolicy;
  private Status status;
  private LoadBalancerIntegration loadBalancerIntegration;
  private List<HealthCheckSpec> healthCheckSpecs;
  private InstanceTemplate instanceTemplate;
  private String serviceAccountId;

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  @Override
  @JsonIgnore
  public Set<String> getLoadBalancers() {
    return Optional.ofNullable(loadBalancerIntegration)
        .map(LoadBalancerIntegration::getBalancers)
        .map(b -> b.stream().map(YandexCloudLoadBalancer::getName).collect(Collectors.toSet()))
        .orElse(Collections.emptySet());
  }

  public Map<String, List<HealthCheckSpec>> getLoadBalancersWithHealthChecks() {
    if (instanceTemplate.metadata == null) {
      return Collections.emptyMap();
    }
    if (!instanceTemplate.metadata.containsKey(LOAD_BALANCERS_SPECS)) {
      return Collections.emptyMap();
    }
    return Arrays.stream(instanceTemplate.metadata.get(LOAD_BALANCERS_SPECS).split(";"))
        .map(part -> part.split("="))
        .filter(lbParts -> lbParts.length == 2)
        .collect(
            toMap(
                lbParts -> lbParts[0],
                lbParts ->
                    Arrays.stream(lbParts[1].split("&"))
                        .map(HealthCheckSpec::deserializeFromMetadataValue)
                        .collect(Collectors.toList())));
  }

  public static String serializeLoadBalancersWithHealthChecks(
      Map<String, List<HealthCheckSpec>> balancers) {
    return balancers.keySet().stream()
        .map(
            balancer ->
                balancer
                    + "="
                    + balancers.get(balancer).stream()
                        .map(HealthCheckSpec::serializeForMetadataValue)
                        .collect(joining("&")))
        .collect(joining(";"));
  }

  public enum Status {
    STATUS_UNSPECIFIED,

    // Instance group is being started and will become active soon.
    STARTING,

    // Instance group is active.
    // In this state the group manages its instances and monitors their health,
    // creating, deleting, stopping, updating and starting instances as needed.
    // To stop the instance group, call
    // [yandex.cloud.compute.v1.instancegroup.InstanceGroupService.Stop].
    ACTIVE,

    // Instance group is being stopped.
    // Group's instances stop receiving traffic from the load balancer (if any) and are then
    // stopped.
    STOPPING,

    // Instance group is stopped.
    // In this state the group cannot be updated and does not react to any changes made to its
    // instances.
    // To start the instance group, call
    // [yandex.cloud.compute.v1.instancegroup.InstanceGroupService.Start].
    STOPPED,

    // Instance group is being deleted.
    DELETING;

    public static Status valueOf(int number) {
      Status[] values = values();
      return values.length <= number ? STATUS_UNSPECIFIED : values[number];
    }
  }

  @Data
  public static class AutoScalePolicy {
    // Lower limit for instance count in each zone.
    long minZoneSize;

    // Upper limit for total instance count (across all zones).
    long maxSize;

    // Time in seconds allotted for averaging metrics.
    Duration measurementDuration;

    // The warmup time of the instance in seconds. During this time,
    // traffic is sent to the instance, but instance metrics are not collected.
    Duration warmupDuration;

    // Minimum amount of time in seconds allotted for monitoring before
    // Instance Groups can reduce the number of instances in the group.
    // During this time, the group size doesn't decrease, even if the new metric values
    // indicate that it should.
    Duration stabilizationDuration;

    // Initial target group size.
    long initialSize;

    // Defines an autoscaling rule based on the average CPU utilization of the instance group.
    CpuUtilizationRule cpuUtilizationRule;

    // Defines an autoscaling rule based on a custom metric from Yandex Monitoring.
    List<CustomRule> customRules;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class CpuUtilizationRule {
    // Target CPU utilization level. Instance Groups maintains this level for each availability
    // zone.
    double utilizationTarget = 1;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class CustomRule {
    // Custom metric rule type. This field affects which label from
    // the custom metric should be used: `zone_id` or `instance_id`.
    RuleType ruleType;

    // Type of custom metric. This field affects how Instance Groups calculates the average metric
    // value.
    MetricType metricType;

    // Name of custom metric in Yandex Monitoring that should be used for scaling.
    String metricName;

    // Target value for the custom metric. Instance Groups maintains this level for each
    // availability zone.
    double target;

    public enum RuleType {
      RULE_TYPE_UNSPECIFIED,

      // This type means that the metric applies to one instance.
      // First, Instance Groups calculates the average metric value for each instance,
      // then averages the values for instances in one availability zone.
      // This type of metric must have the `instance_id` label.
      UTILIZATION,

      // This type means that the metric applies to instances in one availability zone.
      // This type of metric must have the `zone_id` label.
      WORKLOAD;

      public static RuleType valueOf(int number) {
        RuleType[] values = values();
        return values.length <= number ? RULE_TYPE_UNSPECIFIED : values[number];
      }
    }

    public enum MetricType {
      METRIC_TYPE_UNSPECIFIED,

      // This type is used for metrics that show the metric value at a certain point in time,
      // such as requests per second to the server on an instance.
      GAUGE,

      // This type is used for metrics that monotonically increase over time,
      // such as the total number of requests to the server on an instance.
      COUNTER;

      public static MetricType valueOf(int number) {
        MetricType[] values = values();
        return values.length <= number ? METRIC_TYPE_UNSPECIFIED : values[number];
      }
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class DeployPolicy {
    // The maximum number of running instances that can be taken offline (i.e., stopped or deleted)
    // at the same time
    // during the update process.
    long maxUnavailable;

    // The maximum number of instances that can be temporarily allocated above the group's target
    // size
    // during the update process.
    long maxExpansion;

    // The maximum number of instances that can be deleted at the same time.
    long maxDeleting;

    // The maximum number of instances that can be created at the same time.
    long maxCreating;

    // Instance startup duration.
    // Instance will be considered up and running (and start receiving traffic) only after
    // startupDuration
    // has elapsed and all health checks are passed.
    Duration startupDuration;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class LoadBalancerIntegration {
    // ID of the target group used for load balancing.
    String targetGroupId;

    // Status message of the target group.
    String statusMessage;

    // Specification of the target group that the instance group will be added to. For more
    // information, see [Target groups and resources](/docs/load-balancer/target-resources).
    TargetGroupSpec targetGroupSpec;

    Set<YandexCloudLoadBalancer> balancers;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class TargetGroupSpec {
    String name;
    String description;
    Map<String, String> labels;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class HealthCheckSpec {
    Type type;
    long port;
    String path;
    Duration interval;
    Duration timeout;
    long unhealthyThreshold;
    long healthyThreshold;

    public enum Type {
      TCP,
      HTTP
    }

    String serializeForMetadataValue() {
      return type.name().toLowerCase()
          + ","
          + port
          + ","
          + path
          + ","
          + interval.getSeconds()
          + ","
          + timeout.getSeconds()
          + ","
          + unhealthyThreshold
          + ","
          + healthyThreshold;
    }

    static HealthCheckSpec deserializeFromMetadataValue(String value) {
      String[] parts = value.split(",");
      if (parts.length != 7) {
        throw new IllegalStateException("Wrong format of health-check stored in metadata");
      }
      HealthCheckSpec healthCheckSpec = new HealthCheckSpec();
      healthCheckSpec.setType(Type.valueOf(parts[0].toUpperCase()));
      healthCheckSpec.setPort(Integer.parseInt(parts[1]));
      healthCheckSpec.setPath(parts[2]);
      healthCheckSpec.setInterval(Duration.ofSeconds(Long.parseLong(parts[3])));
      healthCheckSpec.setTimeout(Duration.ofSeconds(Long.parseLong(parts[4])));
      healthCheckSpec.setUnhealthyThreshold(Long.parseLong(parts[5]));
      healthCheckSpec.setHealthyThreshold(Long.parseLong(parts[6]));
      return healthCheckSpec;
    }
  }

  @Data
  @Builder(toBuilder = true)
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InstanceTemplate {
    String description;
    Map<String, String> labels;
    String platformId;
    ResourcesSpec resourcesSpec;
    Map<String, String> metadata;
    AttachedDiskSpec bootDiskSpec;
    List<AttachedDiskSpec> secondaryDiskSpecs;
    List<NetworkInterfaceSpec> networkInterfaceSpecs;
    SchedulingPolicy schedulingPolicy;
    String serviceAccountId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchedulingPolicy {
      boolean preemptible;
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ResourcesSpec {
    long memory;
    long cores;
    long coreFraction;
    long gpus;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class AttachedDiskSpec {
    Mode mode;
    String deviceName;
    DiskSpec diskSpec;

    public enum Mode {
      MODE_UNSPECIFIED,
      READ_ONLY,
      READ_WRITE;

      public static Mode valueOf(int number) {
        Mode[] values = values();
        return values.length <= number ? MODE_UNSPECIFIED : values[number];
      }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DiskSpec {
      String description;
      String typeId;
      long size;
      String imageId;
      String snapshotId;
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class NetworkInterfaceSpec {
    String networkId;
    List<String> subnetIds;
    PrimaryAddressSpec primaryV4AddressSpec;
    PrimaryAddressSpec primaryV6AddressSpec;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class PrimaryAddressSpec {
    boolean oneToOneNat;
  }
}
