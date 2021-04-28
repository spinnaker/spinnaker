package com.netflix.spinnaker.orca.clouddriver.model;

import lombok.Data;

@Data
public class ServerGroup {
  public String name;
  public String account;
  public String region;
  public String cluster;
  public String cloudProvider;

  public Moniker moniker;
  public Long createdTime;

  public Capacity capacity;

  @Data
  public static class Moniker {
    public String app;
  }

  @Data
  public static class Capacity {
    public Integer min;
    public Integer desired;
    public Integer max;
  }
}
