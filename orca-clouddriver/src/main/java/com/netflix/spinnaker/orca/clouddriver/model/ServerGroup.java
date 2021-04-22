package com.netflix.spinnaker.orca.clouddriver.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.RollbackServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
public class ServerGroup {
  public String name;
  public String account;
  public String region;
  public String cluster;
  public String cloudProvider;
  public String servingStatus;
  public List<String> zones; // GCE only?
  public String zone;
  public String namespace;
  private String serverGroupName; // Is this real?
  private String type;

  public Moniker moniker;
  public Long createdTime;

  public Capacity capacity;

  public List<Instance> instances;

  /**
   * For some reason TargetServerGroup allows looking at 2 properties: {@link
   * TargetServerGroup#isDisabled()} } *
   */
  @JsonAlias("isDisabled")
  public Boolean disabled;

  public Map<String, Object> launchConfig;
  public Asg asg;
  public Integer minSize;
  public Map<String, Object> autoscalingPolicy;
  public String credentials;

  public Image image;
  public BuildInfo buildInfo;

  public String getImageName() {
    return (image != null && image.name != null) ? image.name : null;
  }

  public String getBuildNumber() {
    return (buildInfo != null && buildInfo.jenkins != null) ? buildInfo.jenkins.number : null;
  }

  public List<String> getSuspendedProcesses() {
    return asg.getSuspendedProcesses().stream()
        .map(Process::getProcessName)
        .collect(Collectors.toList());
  }

  @Data
  @Builder
  @JsonDeserialize(builder = Capacity.CapacityBuilder.class)
  public static class Capacity {
    public Integer min;
    public Integer desired;
    public Integer max;

    @JsonPOJOBuilder(withPrefix = "")
    public static class CapacityBuilder {}
  }

  @Data
  public static class Asg {
    public Object desiredCapacity;
    public Object minSize;
    public Object maxSize;
    public List<Process> suspendedProcesses;
  }

  @Data
  public static class Process {
    public String processName;
  }

  @Data
  public static class Image {
    public String imageId;
    public String name;
  }

  @Data
  public static class BuildInfo {
    public Jenkins jenkins;

    @Data
    public static class Jenkins {
      public String number;
    }
  }

  public static class RollbackDetails {
    public RollbackServerGroupStage.RollbackType rollbackType;
    public Map<String, String> rollbackContext;

    public String imageName;
    public String buildNumber;

    public RollbackDetails(
        RollbackServerGroupStage.RollbackType rollbackType,
        Map<String, String> rollbackContext,
        String imageName,
        String buildNumber) {
      this.rollbackType = rollbackType;
      this.rollbackContext = rollbackContext;
      this.imageName = imageName;
      this.buildNumber = buildNumber;
    }

    public RollbackDetails(String imageName, String buildNumber) {
      this.imageName = imageName;
      this.buildNumber = buildNumber;
    }
  }
}
