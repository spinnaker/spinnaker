package com.netflix.spinnaker.halyard.config.model.v1.providers.openstack;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OpenstackAccount extends Account {
  private String accountName;
  private String environment;
  private String accountType;
  private String authUrl;
  private String username;
  private String password;
  private String projectName;
  private String domainName;
  private Boolean insecure = false;
  private String heatTemplateLocation;
  private String consulConfig;

  @LocalFile
  private String userDataFile;
  private OpenstackLbaasOptions lbaas = new OpenstackLbaasOptions();
  private List<String> regions;

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  @Data
  public static class OpenstackLbaasOptions {
    private Integer pollTimeout = 60;
    private Integer pollInterval = 5;
  }
}
