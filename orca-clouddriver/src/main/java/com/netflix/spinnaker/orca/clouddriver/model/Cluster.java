package com.netflix.spinnaker.orca.clouddriver.model;

import java.util.List;
import lombok.Data;

@Data
public class Cluster {
  private List<ServerGroup> serverGroups;
}
