package com.netflix.spinnaker.halyard.config.model.v1.providers.openstack;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OpenstackAccount extends Account {
  private String accountName;
  private String accountType;
  private String authUrl;
  private String username;
  @Secret private String password;
  private String projectName;
  private String domainName;
  private Boolean insecure = false;
  private String heatTemplateLocation;
  private String consulConfig;

  @LocalFile private String userDataFile;
  private OpenstackLbaasOptions lbaas = new OpenstackLbaasOptions();
  private List<String> regions;

  @Data
  public static class OpenstackLbaasOptions {
    private Integer pollTimeout = 60;
    private Integer pollInterval = 5;
  }
}
